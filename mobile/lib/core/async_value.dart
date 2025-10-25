sealed class AsyncValue<T> {
  const AsyncValue();

  const factory AsyncValue.loading() = AsyncLoading<T>;

  const factory AsyncValue.data(T value) = AsyncData<T>;

  const factory AsyncValue.error(Object error) = AsyncError<T>;

  static AsyncValue<T> guard<T>(T Function() fn) {
    try {
      return AsyncValue.data(fn());
    } catch (error) {
      return AsyncValue.error(error);
    }
  }

  static Future<AsyncValue<T>> guardAsync<T>(Future<T> Function() fn) async {
    try {
      return AsyncValue.data(await fn());
    } catch (error) {
      return AsyncValue.error(error);
    }
  }

  R when<R>({
    required R Function() loading,
    required R Function(T data) data,
    required R Function(Object error) error,
  }) {
    return switch (this) {
      AsyncLoading<T>() => loading(),
      AsyncData<T>(value: final value) => data(value),
      AsyncError<T>(error: final errorObj) => error(errorObj),
    };
  }

  T valueOrDefault(T defaultValue) {
    return switch (this) {
      AsyncData<T>(value: final data) => data,
      _ => defaultValue,
    };
  }

  T get valueOrThrow {
    return switch (this) {
      AsyncData<T>(value: final data) => data,
      AsyncError<T>(error: final error) => throw error,
      AsyncLoading<T>() => throw StateError('Value is still loading'),
    };
  }

  T? get valueOrNull => switch (this) {
    AsyncData<T>(value: final data) => data,
    _ => null,
  };

  @override
  String toString() {
    return switch (this) {
      AsyncLoading<T>() => 'AsyncValue<$T>.loading()',
      AsyncData<T>(value: final value) => 'AsyncValue<$T>.data($value)',
      AsyncError<T>(error: final error) => 'AsyncValue<$T>.error($error)',
    };
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return switch ((this, other)) {
      (AsyncLoading<T>(), AsyncLoading<T>()) => true,
      (AsyncData<T>(value: final value1), AsyncData<T>(value: final value2)) =>
        value1 == value2,
      (
        AsyncError<T>(error: final error1),
        AsyncError<T>(error: final error2),
      ) =>
        error1 == error2,
      _ => false,
    };
  }

  @override
  int get hashCode {
    return switch (this) {
      AsyncLoading<T>() => 0,
      AsyncData<T>(value: final value) => value.hashCode,
      AsyncError<T>(error: final error) => error.hashCode,
    };
  }
}

final class AsyncLoading<T> extends AsyncValue<T> {
  const AsyncLoading();
}

final class AsyncData<T> extends AsyncValue<T> {
  const AsyncData(this.value);

  final T value;
}

final class AsyncError<T> extends AsyncValue<T> {
  const AsyncError(this.error);

  final Object error;
}
