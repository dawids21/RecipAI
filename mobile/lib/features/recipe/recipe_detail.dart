import 'package:recipai_mobile/shared/user_role.dart';

class RecipeImage {
  final String id;
  final String url;
  final String thumbnailUrl;

  const RecipeImage({
    required this.id,
    required this.url,
    required this.thumbnailUrl,
  });

  factory RecipeImage.fromJson(Map<String, dynamic> json) {
    return RecipeImage(
      id: json['id'] as String,
      url: json['url'] as String,
      thumbnailUrl: json['thumbnailUrl'] as String,
    );
  }

  Map<String, dynamic> toJson() {
    return {'id': id, 'url': url, 'thumbnailUrl': thumbnailUrl};
  }
}

class Ingredient {
  final String name;
  final String quantity;
  final String? unit;

  const Ingredient({required this.name, required this.quantity, this.unit});

  factory Ingredient.fromJson(Map<String, dynamic> json) {
    return Ingredient(
      name: json['name'] as String,
      quantity: json['quantity'] as String,
      unit: json['unit'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    return {'name': name, 'quantity': quantity, 'unit': unit};
  }
}

class Instruction {
  final String step;

  const Instruction({required this.step});

  factory Instruction.fromJson(Map<String, dynamic> json) {
    return Instruction(step: json['step'] as String);
  }

  Map<String, dynamic> toJson() {
    return {'step': step};
  }
}

class RecipeData {
  final List<Ingredient> ingredients;
  final List<Instruction> instructions;
  final String? sourceUrl;
  final int servingSize;

  const RecipeData({
    required this.ingredients,
    required this.instructions,
    required this.servingSize,
    this.sourceUrl,
  });

  factory RecipeData.fromJson(Map<String, dynamic> json) {
    return RecipeData(
      ingredients: (json['ingredients'] as List<dynamic>)
          .map(
            (ingredient) =>
                Ingredient.fromJson(ingredient as Map<String, dynamic>),
          )
          .toList(),
      instructions: (json['instructions'] as List<dynamic>)
          .map(
            (instruction) =>
                Instruction.fromJson(instruction as Map<String, dynamic>),
          )
          .toList(),
      sourceUrl: json['sourceUrl'] as String?,
      servingSize: json['servingSize'] as int,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'ingredients': ingredients
          .map((ingredient) => ingredient.toJson())
          .toList(),
      'instructions': instructions
          .map((instruction) => instruction.toJson())
          .toList(),
      'sourceUrl': sourceUrl,
      'servingSize': servingSize,
    };
  }
}

class RecipeDetail {
  final String id;
  final String name;
  final RecipeData data;
  final UserRole role;
  final String? collectionId;
  final String? collectionName;
  final List<RecipeImage> images;

  const RecipeDetail({
    required this.id,
    required this.name,
    required this.data,
    required this.role,
    this.collectionId,
    this.collectionName,
    this.images = const [],
  });

  factory RecipeDetail.fromJson(Map<String, dynamic> json) {
    return RecipeDetail(
      id: json['id'] as String,
      name: json['name'] as String,
      data: RecipeData.fromJson(json['data'] as Map<String, dynamic>),
      role: UserRole.fromApiString(json['role'] as String),
      collectionId: json['collectionId'] as String?,
      collectionName: json['collectionName'] as String?,
      images: json['images'] != null
          ? (json['images'] as List<dynamic>)
                .map(
                  (image) =>
                      RecipeImage.fromJson(image as Map<String, dynamic>),
                )
                .toList()
          : [],
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'data': data.toJson(),
      'role': role.toApiString(),
      'recipesCollectionId': collectionId,
      'images': images.map((image) => image.toJson()).toList(),
    };
  }
}

class RecipeRequest {
  final String name;
  final String? recipesCollectionId;
  final RecipeData data;
  final List<String> images;

  const RecipeRequest({
    required this.name,
    this.recipesCollectionId,
    required this.data,
    required this.images,
  });

  Map<String, dynamic> toJson() {
    return {
      'name': name,
      'recipesCollectionId': recipesCollectionId,
      'data': data.toJson(),
      'images': images,
    };
  }
}
