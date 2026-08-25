class LimitBalance {
  final int used;
  final int? resetsInSeconds;

  const LimitBalance({required this.used, this.resetsInSeconds});

  factory LimitBalance.fromJson(Map<String, dynamic> json) {
    return LimitBalance(
      used: json['used'] as int,
      resetsInSeconds: json['resetsInSeconds'] as int?,
    );
  }
}
