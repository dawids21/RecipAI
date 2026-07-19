import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:logging/logging.dart';
import 'package:synchronized/synchronized.dart';

import '../auth/auth_service.dart';
import 'shopping_list_item_dao.dart';
import 'shopping_list_item_repository.dart';
import 'shopping_list_item_store_service.dart';

/// Per-list sync state, rendered as the detail screen's list-level indicator
/// (and, for [failure], the persistent bottom retry banner).
enum SyncStatus { syncing, notSyncing, failure, offline }

/// Why a queued change was dropped, driving the rejection toast copy.
enum RejectionOutcome { conflict, gone, rejected }

/// Outcome of pushing a single outbox entry, telling [_drainPass] whether to
/// keep draining ([pushed]), stop because the queue is empty ([empty]), or stop
/// because it stalled on a transient/offline failure ([stalled]).
enum _PushResult { empty, pushed, stalled }

/// A view-drained notification of a dropped outbox entry. The screen for
/// [listId], if open, renders a toast; otherwise the event has no subscriber
/// and is dropped (the store has already rolled the item back).
class RejectionEvent {
  final String listId;
  final String itemName;
  final RejectionOutcome outcome;

  const RejectionEvent(this.listId, this.itemName, this.outcome);
}

/// App-level singleton that owns push: drains each list's outbox to the
/// item write endpoints FIFO, one entry at a time, sequentially per list —
/// different lists drain concurrently. Reconciles accepts, cascade-discards
/// on 412, discards on a permanent 4xx, and retries transient failures with
/// backoff before surfacing a per-list [SyncStatus.failure].
///
/// UI-agnostic: exposes state ([syncStatusFor]) and events ([rejections])
/// only; the detail screen owns all rendering.
class ShoppingListSyncService with WidgetsBindingObserver {
  static final _log = Logger('recipai.shopping_list.sync');
  static const _maxRetries = 5;
  static const _pollInterval = Duration(seconds: 10);

  final ShoppingListItemRepository _itemRepository;
  final ShoppingListItemStoreService _store;
  final AuthService _authService;

  ShoppingListSyncService({
    required ShoppingListItemRepository itemRepository,
    required ShoppingListItemStoreService store,
    required AuthService authService,
  }) : _itemRepository = itemRepository,
       _store = store,
       _authService = authService;

  /// Per-list sync lock, held across one poll (fetch+reconcile) OR one push
  /// entry (fetch+push+ack) — never across backoff waits — so a poll's reconcile
  /// can never straddle a push's ack. Released between operations, so cross-list
  /// concurrency and per-entry interleaving are preserved (ADR-0004). This is an
  /// outer lock over the store's per-list lock; the two never deadlock because
  /// the store never calls back into the sync service.
  final _syncLocks = <String, Lock>{};

  /// Single-flight-drain guard — at most one drain loop per list; a kick
  /// arriving mid-drain is coalesced into [_pending] (§Serialization).
  final _draining = <String>{};
  final _pending = <String>{};
  final _retry = <String, int>{};
  final _backoffTimers = <String, Timer>{};
  final _offlineTimers = <String, Timer>{};
  final _pollTimers = <String, Timer>{};
  final _status = <String, ValueNotifier<SyncStatus>>{};
  final _rejections = StreamController<RejectionEvent>.broadcast();

  /// Broadcast rejection events; dropped if nothing is listening.
  Stream<RejectionEvent> get rejections => _rejections.stream;

  /// This list's sync status, lazily created (default [SyncStatus.notSyncing])
  /// on first access.
  ValueListenable<SyncStatus> syncStatusFor(String listId) =>
      _statusNotifier(listId);

  ValueNotifier<SyncStatus> _statusNotifier(String listId) {
    return _status.putIfAbsent(
      listId,
      () => ValueNotifier(SyncStatus.notSyncing),
    );
  }

  void _setStatus(String listId, SyncStatus status) {
    _statusNotifier(listId).value = status;
  }

  Lock _syncLockFor(String listId) =>
      _syncLocks.putIfAbsent(listId, () => Lock());

  /// Coalesced per-list kick — append / openList / resume / backoff / retry
  /// all call this. A kick arriving mid-drain is never lost: it sets
  /// [_pending], which [_drain] re-checks before it clears [_draining].
  void requestDrain(String listId) {
    if (_draining.contains(listId)) {
      _pending.add(listId);
      return;
    }
    unawaited(_drain(listId));
  }

  /// Resets [listId]'s retry counter and re-kicks its drain (from its
  /// failure banner's retry button).
  Future<void> retry(String listId) async {
    _retry[listId] = 0;
    _backoffTimers.remove(listId)?.cancel();
    requestDrain(listId);
  }

  /// Registers the app-lifecycle observer and fans out a drain over every
  /// list with a pending outbox — so offline edits to a list the user isn't
  /// viewing still flush.
  Future<void> start() async {
    WidgetsBinding.instance.addObserver(this);
    await _fanOutPending();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.inactive) {
      for (final timer in _pollTimers.values) {
        timer.cancel();
      }
    } else if (state == AppLifecycleState.resumed) {
      unawaited(_fanOutPending());
      for (final listId in _pollTimers.keys.toList()) {
        _pollTimers[listId]?.cancel();
        _pollTimers[listId] = Timer.periodic(
          _pollInterval,
          (_) => unawaited(_poll(listId)),
        );
        unawaited(_poll(listId));
      }
    }
  }

  Future<void> _fanOutPending() async {
    final listIds = await _store.listIdsWithOutbox();
    for (final listId in listIds) {
      requestDrain(listId);
    }
  }

  /// Starts polling [listId]: an immediate poll (the cold-start item load)
  /// then a periodic timer every [_pollInterval].
  void startPolling(String listId) {
    _pollTimers.remove(listId)?.cancel();
    unawaited(_poll(listId));
    _pollTimers[listId] = Timer.periodic(
      _pollInterval,
      (_) => unawaited(_poll(listId)),
    );
  }

  /// Stops polling [listId] (screen closed).
  void stopPolling(String listId) {
    _pollTimers.remove(listId)?.cancel();
  }

  Future<void> _poll(String listId) async {
    try {
      // Fetch and reconcile under the sync lock as one unit, so a concurrent
      // push's ack can never land between this snapshot and its reconcile and
      // make the reconcile's deletion/insert pass act on stale server state.
      final items = await _syncLockFor(listId).synchronized(() async {
        final token = await _authService.idToken;
        final items = await _itemRepository.fetchServerItems(listId, token);
        await _store.reconcileFromServer(listId, items);
        return items;
      });
      requestDrain(listId);
      _log.fine('poll ok (listId=$listId, items=${items.length})');
    } on ShoppingListNetworkException {
      _log.fine('poll offline (listId=$listId)');
    } catch (e) {
      _log.warning('poll failed, store untouched (listId=$listId)', e);
    }
  }

  Future<void> _drain(String listId) async {
    _draining.add(listId);
    _log.fine('drain start (listId=$listId)');
    try {
      do {
        _pending.remove(listId);
        final drainedEmpty = await _drainPass(listId);
        if (!drainedEmpty) {
          return; // stalled on transient; backoff/failure/offline set
        }
      } while (_pending.contains(listId));
      _setStatus(listId, SyncStatus.notSyncing);
      _log.fine('drain idle (listId=$listId)');
    } finally {
      _draining.remove(listId);
    }
  }

  /// Drains [listId]'s queue one entry at a time. Returns `true` once the
  /// queue is empty, `false` if it stalled on a transient failure (the head
  /// entry blocks this list's queue until backoff/retry).
  ///
  /// Each entry is pushed under the sync lock (fetch+push+ack as one unit); the
  /// lock is released between entries and before any backoff wait, so a poll can
  /// interleave between two pushes but never straddle a single push's ack.
  Future<bool> _drainPass(String listId) async {
    while (true) {
      final result = await _syncLockFor(
        listId,
      ).synchronized(() => _pushHeadEntry(listId));
      switch (result) {
        case _PushResult.empty:
          return true;
        case _PushResult.stalled:
          return false;
        case _PushResult.pushed:
          continue; // drain the next entry under a fresh lock acquisition
      }
    }
  }

  /// Pushes [listId]'s head outbox entry and reconciles the outcome, all under
  /// the caller-held sync lock. Arming a backoff timer only schedules a future
  /// kick — it does not wait — so the lock is not held across the backoff.
  Future<_PushResult> _pushHeadEntry(String listId) async {
    final entry = await _store.nextOutboxEntry(listId);
    if (entry == null) return _PushResult.empty;
    _setStatus(listId, SyncStatus.syncing);

    try {
      await _pushOne(entry);
      _retry[listId] = 0;
      _offlineTimers.remove(listId)?.cancel();
      return _PushResult.pushed;
    } on ItemVersionConflictException catch (e) {
      await _store.cascadeDiscard(listId, entry.itemLocalId, e.winner);
      _log.warning(
        'Item push rejected: conflict (listId=$listId, itemLocalId=${entry.itemLocalId})',
      );
      _emit(RejectionEvent(listId, e.winner.name, RejectionOutcome.conflict));
      _offlineTimers.remove(listId)?.cancel();
      return _PushResult.pushed;
    } on ItemDiscardedException catch (e) {
      final item = await _store.readItem(entry.itemLocalId);
      await _store.discardItem(listId, entry.itemLocalId);
      final outcome = e.reason == DiscardReason.gone
          ? RejectionOutcome.gone
          : RejectionOutcome.rejected;
      _log.severe(
        'Item push discarded: ${e.reason.name} (listId=$listId, itemLocalId=${entry.itemLocalId})',
      );
      _emit(RejectionEvent(listId, item?.name ?? '', outcome));
      _offlineTimers.remove(listId)?.cancel();
      return _PushResult.pushed;
    } on ShoppingListNetworkException {
      _setStatus(listId, SyncStatus.offline);
      _armOfflineTimer(listId);
      _log.fine(
        'Item push offline, entry retries on next signal (listId=$listId)',
      );
      return _PushResult.stalled;
    } catch (e) {
      final attempt = (_retry[listId] ?? 0) + 1;
      _retry[listId] = attempt;
      if (attempt <= _maxRetries) {
        _log.warning(
          'Item push failed transiently, retry $attempt/$_maxRetries (listId=$listId)',
          e,
        );
        _armBackoffTimer(listId, attempt);
      } else {
        _log.warning(
          'Item push failed, list entering failure state (listId=$listId)',
          e,
        );
        _setStatus(listId, SyncStatus.failure);
      }
      return _PushResult.stalled;
    }
  }

  Future<void> _pushOne(OutboxEntry entry) async {
    final item = (await _store.readItem(entry.itemLocalId))!;
    final token = await _authService.idToken;
    switch (entry.kind) {
      case OutboxKind.create:
        final winner = await _itemRepository.createItem(
          entry.listId,
          OutboxPayload.fromMap(entry.payload),
          token,
        );
        await _store.reconcileAck(
          entry.listId,
          entry.itemLocalId,
          winner,
          entry.seq,
        );
      case OutboxKind.update:
        final winner = await _itemRepository.updateItem(
          entry.listId,
          item.serverId!,
          baseVersion: item.lastAckedVersion!,
          snapshot: OutboxPayload.fromMap(entry.payload),
          idToken: token,
        );
        await _store.reconcileAck(
          entry.listId,
          entry.itemLocalId,
          winner,
          entry.seq,
        );
      case OutboxKind.delete:
        await _itemRepository.deleteItem(
          entry.listId,
          item.serverId!,
          item.lastAckedVersion!,
          token,
        );
        await _store.reconcileDeleteAck(
          entry.listId,
          entry.itemLocalId,
          entry.seq,
        );
    }
  }

  void _armBackoffTimer(String listId, int attempt) {
    _backoffTimers.remove(listId)?.cancel();
    final seconds = math.min(math.pow(2, attempt - 1).toInt(), 30);
    _backoffTimers[listId] = Timer(Duration(seconds: seconds), () {
      _backoffTimers.remove(listId);
      requestDrain(listId);
    });
  }

  /// Re-kicks a stalled offline drain at a fixed interval, so closed lists
  /// (which don't poll) still flush once connectivity returns. Offline is not
  /// a failure: this never touches [_retry] and never escalates to
  /// [SyncStatus.failure].
  void _armOfflineTimer(String listId) {
    if (_offlineTimers.containsKey(listId)) return;
    _offlineTimers[listId] = Timer.periodic(_pollInterval, (_) {
      requestDrain(listId);
    });
  }

  void _emit(RejectionEvent event) {
    if (!_rejections.isClosed) {
      _rejections.add(event);
    }
  }

  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    for (final timer in _backoffTimers.values) {
      timer.cancel();
    }
    _backoffTimers.clear();
    for (final timer in _offlineTimers.values) {
      timer.cancel();
    }
    _offlineTimers.clear();
    for (final timer in _pollTimers.values) {
      timer.cancel();
    }
    _pollTimers.clear();
    for (final notifier in _status.values) {
      notifier.dispose();
    }
    _rejections.close();
  }
}
