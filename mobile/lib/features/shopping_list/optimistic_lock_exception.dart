class OptimisticLockException implements Exception {
  final String message;

  OptimisticLockException(this.message);

  @override
  String toString() => message;
}
