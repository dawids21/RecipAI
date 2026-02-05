import '../../shared/user_role.dart';
import '../recipe/recipe_detail.dart';

class ExtractedIngredient {
  final String name;
  final String quantity;
  final String? unit;

  const ExtractedIngredient({
    required this.name,
    required this.quantity,
    this.unit,
  });

  factory ExtractedIngredient.fromJson(Map<String, dynamic> json) {
    return ExtractedIngredient(
      name: json['name'] as String,
      quantity: json['quantity'] as String,
      unit: json['unit'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    return {'name': name, 'quantity': quantity, 'unit': unit};
  }

  Ingredient toIngredient() {
    return Ingredient(name: name, quantity: quantity, unit: unit);
  }
}

class ExtractedInstruction {
  final String step;

  const ExtractedInstruction({required this.step});

  factory ExtractedInstruction.fromJson(Map<String, dynamic> json) {
    return ExtractedInstruction(step: json['step'] as String);
  }

  Map<String, dynamic> toJson() {
    return {'step': step};
  }

  Instruction toInstruction() {
    return Instruction(step: step);
  }
}

class ExtractedRecipe {
  final String name;
  final List<ExtractedIngredient> ingredients;
  final List<ExtractedInstruction> instructions;
  final int? servingSize;

  const ExtractedRecipe({
    required this.name,
    required this.ingredients,
    required this.instructions,
    this.servingSize,
  });

  factory ExtractedRecipe.fromJson(Map<String, dynamic> json) {
    return ExtractedRecipe(
      name: json['name'] as String,
      ingredients: (json['ingredients'] as List<dynamic>)
          .map(
            (ingredient) => ExtractedIngredient.fromJson(
              ingredient as Map<String, dynamic>,
            ),
          )
          .toList(),
      instructions: (json['instructions'] as List<dynamic>)
          .map(
            (instruction) => ExtractedInstruction.fromJson(
              instruction as Map<String, dynamic>,
            ),
          )
          .toList(),
      servingSize: json['servingSize'] as int?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'name': name,
      'ingredients': ingredients
          .map((ingredient) => ingredient.toJson())
          .toList(),
      'instructions': instructions
          .map((instruction) => instruction.toJson())
          .toList(),
      'servingSize': servingSize,
    };
  }

  RecipeDetail toRecipeDetail() {
    return RecipeDetail(
      id: '', // Empty for new recipes
      name: name,
      data: RecipeData(
        ingredients: ingredients.map((e) => e.toIngredient()).toList(),
        instructions: instructions.map((e) => e.toInstruction()).toList(),
        servingSize: servingSize ?? 1,
      ),
      role: UserRole.owner,
    );
  }
}
