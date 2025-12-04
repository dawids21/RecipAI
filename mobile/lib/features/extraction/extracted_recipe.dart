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
  final String? description;
  final List<ExtractedIngredient> ingredients;
  final List<ExtractedInstruction> instructions;

  const ExtractedRecipe({
    required this.name,
    this.description,
    required this.ingredients,
    required this.instructions,
  });

  factory ExtractedRecipe.fromJson(Map<String, dynamic> json) {
    return ExtractedRecipe(
      name: json['name'] as String,
      description: json['description'] as String?,
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
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'name': name,
      'description': description,
      'ingredients': ingredients
          .map((ingredient) => ingredient.toJson())
          .toList(),
      'instructions': instructions
          .map((instruction) => instruction.toJson())
          .toList(),
    };
  }

  RecipeDetail toRecipeDetail() {
    return RecipeDetail(
      id: '', // Empty for new recipes
      name: name,
      data: RecipeData(
        ingredients: ingredients.map((e) => e.toIngredient()).toList(),
        instructions: instructions.map((e) => e.toInstruction()).toList(),
      ),
      role: UserRole.owner,
    );
  }
}
