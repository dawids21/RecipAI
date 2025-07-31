class Recipe {
  final String id;
  final String name;

  const Recipe({required this.id, required this.name});

  factory Recipe.fromJson(Map<String, dynamic> json) {
    return Recipe(id: json['id'] as String, name: json['name'] as String);
  }

  Map<String, dynamic> toJson() {
    return {'id': id, 'name': name};
  }
}
