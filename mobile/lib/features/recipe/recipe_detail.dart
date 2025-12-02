import 'package:recipai_mobile/shared/user_role.dart';

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

  const RecipeData({required this.ingredients, required this.instructions});

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

  const RecipeDetail({
    required this.id,
    required this.name,
    required this.data,
    required this.role,
    this.collectionId,
    this.collectionName,
  });

  factory RecipeDetail.fromJson(Map<String, dynamic> json) {
    return RecipeDetail(
      id: json['id'] as String,
      name: json['name'] as String,
      data: RecipeData.fromJson(json['data'] as Map<String, dynamic>),
      role: UserRole.fromApiString(json['role'] as String),
      collectionId: json['collectionId'] as String?,
      collectionName: json['collectionName'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'data': data.toJson(),
      'role': role.toApiString(),
      'recipesCollectionId': collectionId,
    };
  }
}
