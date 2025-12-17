class Recipe {
  final String id;
  final String name;
  final String? thumbnailUrl;

  const Recipe({required this.id, required this.name, this.thumbnailUrl});

  factory Recipe.fromJson(Map<String, dynamic> json) {
    return Recipe(
      id: json['id'] as String,
      name: json['name'] as String,
      thumbnailUrl: json['thumbnailUrl'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    return {'id': id, 'name': name, 'thumbnailUrl': thumbnailUrl};
  }
}
