class ShoppingList {
  final String id;
  final String name;
  final int version;

  const ShoppingList({
    required this.id,
    required this.name,
    required this.version,
  });

  factory ShoppingList.fromJson(Map<String, dynamic> json) {
    return ShoppingList(
      id: json['id'] as String,
      name: json['name'] as String,
      version: json['version'] as int,
    );
  }

  Map<String, dynamic> toJson() {
    return {'id': id, 'name': name, 'version': version};
  }
}
