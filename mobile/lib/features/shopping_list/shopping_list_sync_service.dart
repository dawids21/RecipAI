import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:logging/logging.dart';

import '../auth/auth_service.dart';
import 'shopping_list_item_dao.dart';
import 'shopping_list_item_repository.dart';

/// Per-list push sync state, rendered as the detail screen's persistent
/// bottom failure banner.
enum SyncStatus { syncing, notSyncing, failure }

/// Why a queued change was dropped, driving the rejection toast copy.
enum RejectionOutcome { conflict, gone, rejected }

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

  final ShoppingListItemRepository _itemRepository;
  final AuthService _authService;

  ShoppingListSyncService({
    required ShoppingListItemRepository itemRepository,
    required AuthService authService,
  }) : _itemRepository = itemRepository,
       _authService = authService;

  final _draining = <String>{};
  final _pending = <String>{};
  final _retry = <String, int>{};
  final _backoffTimers = <String, Timer>{};
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
    if (state == AppLifecycleState.resumed) {
      unawaited(_fanOutPending());
    }
  }

  Future<void> _fanOutPending() async {
    final listIds = await _itemRepository.listIdsWithOutbox();
    for (final listId in listIds) {
      requestDrain(listId);
    }
  }

  Future<void> _drain(String listId) async {
    _draining.add(listId);
    _setStatus(listId, SyncStatus.syncing);
    _log.fine('drain start (listId=$listId)');
    try {
      do {
        _pending.remove(listId);
        final drainedEmpty = await _drainPass(listId);
        if (!drainedEmpty) return; // stalled on transient; backoff/failure set
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
  Future<bool> _drainPass(String listId) async {
    while (true) {
      final entry = await _itemRepository.nextOutboxEntry(listId);
      if (entry == null) return true;

      try {
        await _pushOne(entry);
        _retry[listId] = 0;
      } on ItemVersionConflictException catch (e) {
        await _itemRepository.cascadeDiscard(entry.itemLocalId, e.winner);
        _log.warning(
          'Item push rejected: conflict (listId=$listId, itemLocalId=${entry.itemLocalId})',
        );
        _emit(RejectionEvent(listId, e.winner.name, RejectionOutcome.conflict));
      } on ItemDiscardedException catch (e) {
        final item = await _itemRepository.readItem(entry.itemLocalId);
        await _itemRepository.discardItem(entry.itemLocalId);
        final outcome = e.reason == DiscardReason.gone
            ? RejectionOutcome.gone
            : RejectionOutcome.rejected;
        _log.severe(
          'Item push discarded: ${e.reason.name} (listId=$listId, itemLocalId=${entry.itemLocalId})',
        );
        _emit(RejectionEvent(listId, item?.name ?? '', outcome));
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
        return false;
      }
    }
  }

  Future<void> _pushOne(OutboxEntry entry) async {
    final item = (await _itemRepository.readItem(entry.itemLocalId))!;
    final token = await _authService.idToken;
    switch (entry.kind) {
      case OutboxKind.create:
        final winner = await _itemRepository.createItem(
          entry.listId,
          OutboxPayload.fromMap(entry.payload),
          token,
        );
        await _itemRepository.reconcileAck(
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
        await _itemRepository.reconcileAck(
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
        await _itemRepository.reconcileDeleteAck(entry.itemLocalId, entry.seq);
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
    for (final notifier in _status.values) {
      notifier.dispose();
    }
    _rejections.close();
  }
}
