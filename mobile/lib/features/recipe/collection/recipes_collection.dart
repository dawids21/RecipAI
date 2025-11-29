class RecipesCollection {
  final String id;
  final String name;

  const RecipesCollection({required this.id, required this.name});

  factory RecipesCollection.fromJson(Map<String, dynamic> json) {
    return RecipesCollection(
      id: json['id'] as String,
      name: json['name'] as String,
    );
  }

  Map<String, dynamic> toJson() {
    return {'id': id, 'name': name};
  }
}
