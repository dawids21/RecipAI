class LimitUsage {
  final int used;
  final int? resetsInSeconds;

  const LimitUsage({required this.used, this.resetsInSeconds});

  factory LimitUsage.fromJson(Map<String, dynamic> json) {
    return LimitUsage(
      used: json['used'] as int,
      resetsInSeconds: json['resetsInSeconds'] as int?,
    );
  }
}
