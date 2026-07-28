import 'dart:async';

/// Handle to a timer created via [Scheduler]. Its only capability is
/// [cancel], keeping the surface narrow enough for test doubles to implement.
abstract interface class ScheduledTimer {
  void cancel();
}

/// Abstraction over timer creation, injected into services that need timers
/// so tests can supply an inert scheduler instead of real `dart:async` timers.
abstract interface class Scheduler {
  ScheduledTimer periodic(Duration duration, void Function() callback);
  ScheduledTimer oneShot(Duration duration, void Function() callback);
}

class _RealScheduledTimer implements ScheduledTimer {
  final Timer _timer;

  _RealScheduledTimer(this._timer);

  @override
  void cancel() => _timer.cancel();
}

/// Production [Scheduler] backed by real `dart:async` [Timer]s.
class RealScheduler implements Scheduler {
  @override
  ScheduledTimer periodic(Duration duration, void Function() callback) {
    return _RealScheduledTimer(Timer.periodic(duration, (_) => callback()));
  }

  @override
  ScheduledTimer oneShot(Duration duration, void Function() callback) {
    return _RealScheduledTimer(Timer(duration, callback));
  }
}
