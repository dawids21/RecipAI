import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:recipai_mobile/core/routes.dart';
import 'package:recipai_mobile/core/theme.dart';
import 'package:recipai_mobile/features/planning/meal_entry_form_result.dart';
import 'package:recipai_mobile/features/planning/meal_plan_calendar_entry.dart';
import 'package:recipai_mobile/features/planning/meal_plan_list_service.dart';
import 'package:recipai_mobile/features/recipe/collection/recipes_collection_list_service.dart';
import 'package:recipai_mobile/features/recipe/recipe.dart';
import 'package:recipai_mobile/shared/serving_size_input.dart';

class MealEntryFormDialog extends StatefulWidget {
  final MealPlanListService mealPlanListService;
  final RecipesCollectionListService recipesCollectionListService;
  final MealPlanCalendarEntry? existingEntry;
  final DateTime? defaultDate;
  final Recipe? preselectedRecipe;
  final int? preselectedServingSize;

  const MealEntryFormDialog({
    super.key,
    required this.mealPlanListService,
    required this.recipesCollectionListService,
    this.existingEntry,
    this.defaultDate,
    this.preselectedRecipe,
    this.preselectedServingSize,
  });

  @override
  State<MealEntryFormDialog> createState() => _MealEntryFormDialogState();
}

class _MealEntryFormDialogState extends State<MealEntryFormDialog> {
  final _formKey = GlobalKey<FormState>();
  final _placeholderTextController = TextEditingController();

  String? _selectedPlanId;
  DateTime? _selectedDate;
  bool _isRecipeMode = true;
  Recipe? _selectedRecipe;
  String? _planError;
  String? _recipeError;
  int _servingSize = 1;

  bool get _isEditMode => widget.existingEntry?.id != null;

  bool get _isPreselectedRecipe => widget.preselectedRecipe != null;

  @override
  void initState() {
    super.initState();
    _initializeFromExisting();
    _loadPlans();
  }

  @override
  void dispose() {
    _placeholderTextController.dispose();
    super.dispose();
  }

  void _initializeFromExisting() {
    // Handle preselected recipe first
    if (widget.preselectedRecipe != null) {
      _isRecipeMode = true;
      _selectedRecipe = widget.preselectedRecipe;
      _servingSize = widget.preselectedServingSize ?? 1;
      _selectedDate = widget.defaultDate ?? DateTime.now();
    } else if (widget.existingEntry != null) {
      final entry = widget.existingEntry!;
      _selectedPlanId = entry.planId;
      _selectedDate = DateTime.parse(entry.date);
      _isRecipeMode = entry.isRecipeEntry;

      if (entry.isRecipeEntry) {
        _selectedRecipe = Recipe(
          id: entry.recipeId!,
          name: entry.recipeName!,
          thumbnailUrl: null,
        );
        _servingSize = entry.servingSize ?? 1;
      } else {
        _placeholderTextController.text = entry.placeholderText ?? '';
      }
    } else {
      _selectedDate = widget.defaultDate ?? DateTime.now();
    }
  }

  Future<void> _loadPlans() async {
    widget.mealPlanListService.loadMealPlans().then((_) {
      final plans = widget.mealPlanListService.mealPlans.value.valueOrNull;
      if (!_isEditMode && plans != null && _selectedPlanId == null) {
        _selectedPlanId = plans.first.id;
      }
    });
  }

  Future<void> _selectDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _selectedDate ?? DateTime.now(),
      firstDate: DateTime.now().subtract(Duration(days: 30)),
      lastDate: DateTime.now().add(Duration(days: 60)),
    );

    if (picked != null) {
      setState(() {
        _selectedDate = picked;
      });
    }
  }

  Future<void> _selectRecipe() async {
    final recipe = await context.pushNamed<Recipe>(AppRoute.recipePicker.name);

    if (recipe != null) {
      setState(() {
        _selectedRecipe = recipe;
        _recipeError = null;
      });
    }
  }

  void _handleSave() {
    if (_selectedPlanId == null) {
      setState(() {
        _planError = 'Please select a plan';
      });
      return;
    }

    if (_isRecipeMode && _selectedRecipe == null) {
      setState(() {
        _recipeError = 'Please select a recipe';
      });
      return;
    }

    if (!_formKey.currentState!.validate()) {
      return;
    }

    final result = MealEntryFormResult(
      planId: _selectedPlanId!,
      date: _selectedDate!,
      recipeId: _isRecipeMode ? _selectedRecipe?.id : null,
      recipeName: _isRecipeMode ? _selectedRecipe?.name : null,
      servingSize: _isRecipeMode ? _servingSize : null,
      placeholderText: !_isRecipeMode
          ? _placeholderTextController.text.trim()
          : null,
    );

    Navigator.of(context).pop(result);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final locale = Localizations.localeOf(context).toString();
    final dateFormat = DateFormat.yMMMd(locale);
    return ValueListenableBuilder(
      valueListenable: widget.mealPlanListService.mealPlans,
      builder: (context, asyncValuePlans, child) {
        return asyncValuePlans.when(
          loading: () => AlertDialog(
            title: Text(_isEditMode ? 'Edit Meal Entry' : 'Add Meal Entry'),
            content: const Center(child: CircularProgressIndicator()),
          ),
          data: (plans) {
            if (plans.isEmpty) {
              return AlertDialog(
                title: const Text('No Editable Plans'),
                content: const Text(
                  'You need to create a plan with edit permissions first.',
                ),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.of(context).pop(),
                    child: const Text('OK'),
                  ),
                ],
              );
            }
            return AlertDialog(
              title: Text(_isEditMode ? 'Edit Meal Entry' : 'Add Meal Entry'),
              content: Form(
                key: _formKey,
                child: SingleChildScrollView(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      DropdownButtonFormField<String>(
                        initialValue: _selectedPlanId,
                        decoration: InputDecoration(
                          labelText: 'Plan',
                          errorText: _planError,
                        ),
                        items: plans.map((plan) {
                          return DropdownMenuItem<String>(
                            value: plan.id,
                            child: Row(
                              children: [
                                CircleAvatar(
                                  radius: 8,
                                  backgroundColor: plan.color,
                                ),
                                const SizedBox(width: AppSpacing.small),
                                Text(plan.name),
                              ],
                            ),
                          );
                        }).toList(),
                        onChanged: (value) {
                          setState(() {
                            _selectedPlanId = value;
                            _planError = null;
                          });
                        },
                      ),
                      const SizedBox(height: AppSpacing.medium),

                      InkWell(
                        onTap: _selectDate,
                        child: InputDecorator(
                          decoration: const InputDecoration(labelText: 'Date'),
                          child: Text(
                            dateFormat.format(_selectedDate!),
                            style: theme.textTheme.bodyLarge,
                          ),
                        ),
                      ),
                      const SizedBox(height: AppSpacing.medium),

                      // Mode Toggle
                      if (!_isPreselectedRecipe) ...[
                        Text(
                          'Type',
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                        const SizedBox(height: AppSpacing.small),
                        SizedBox(
                          width: double.infinity,
                          child: SegmentedButton<bool>(
                            segments: const [
                              ButtonSegment<bool>(
                                value: true,
                                label: Text('Recipe'),
                                icon: Icon(Icons.restaurant_menu),
                              ),
                              ButtonSegment<bool>(
                                value: false,
                                label: Text('Note'),
                                icon: Icon(Icons.text_fields),
                              ),
                            ],
                            selected: {_isRecipeMode},
                            onSelectionChanged: (Set<bool> newSelection) {
                              setState(() {
                                _isRecipeMode = newSelection.first;
                                // Clear fields when switching modes
                                if (_isRecipeMode) {
                                  _placeholderTextController.clear();
                                } else {
                                  _selectedRecipe = null;
                                  _servingSize = 1;
                                  _recipeError = null;
                                }
                              });
                            },
                          ),
                        ),
                        const SizedBox(height: AppSpacing.medium),
                      ],

                      // Recipe Mode Fields
                      if (_isRecipeMode) ...[
                        // Recipe Selection
                        if (!_isPreselectedRecipe) ...[
                          InkWell(
                            onTap: _selectRecipe,
                            child: InputDecorator(
                              decoration: InputDecoration(
                                labelText: 'Recipe',
                                suffixIcon: const Icon(Icons.search),
                                errorText: _recipeError,
                              ),
                              child: Text(
                                _selectedRecipe?.name ?? 'Select Recipe',
                                style: theme.textTheme.bodyLarge?.copyWith(
                                  color: _selectedRecipe == null
                                      ? theme.hintColor
                                      : null,
                                ),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ),
                          const SizedBox(height: AppSpacing.medium),
                        ],

                        // Serving Size Input
                        ServingSizeInput(
                          servingSize: _servingSize,
                          onChanged: (value) {
                            setState(() {
                              _servingSize = value;
                            });
                          },
                        ),
                      ],

                      // Note Mode Fields
                      if (!_isRecipeMode) ...[
                        TextFormField(
                          controller: _placeholderTextController,
                          decoration: const InputDecoration(
                            labelText: 'Meal Description',
                            hintText: 'Enter meal description',
                          ),
                          validator: (value) {
                            if (value == null || value.trim().isEmpty) {
                              return 'Please enter a description';
                            }
                            return null;
                          },
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('Cancel'),
                ),
                FilledButton(
                  onPressed: _handleSave,
                  child: Text(_isEditMode ? 'Save' : 'Create'),
                ),
              ],
            );
          },
          error: (error) => AlertDialog(
            title: const Text('Error while loading meal plans'),
            content: const Text('Try again later'),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(context).pop(),
                child: const Text('OK'),
              ),
            ],
          ),
        );
      },
    );
  }
}
