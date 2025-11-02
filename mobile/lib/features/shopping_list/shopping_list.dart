class ShoppingList {
  final String id;
  final String name;

  const ShoppingList({required this.id, required this.name});

  factory ShoppingList.fromJson(Map<String, dynamic> json) {
    return ShoppingList(id: json['id'] as String, name: json['name'] as String);
  }

  Map<String, dynamic> toJson() {
    return {'id': id, 'name': name};
  }
}
