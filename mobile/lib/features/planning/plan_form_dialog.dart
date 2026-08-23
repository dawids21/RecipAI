import 'package:flutter/material.dart';
import 'package:recipai_mobile/features/planning/plan_color_picker.dart';

import '../../core/theme.dart';
import '../limits/limit_cap.dart';
import '../limits/limit_counter.dart';
import '../limits/limit_gate.dart';
import '../limits/limits_service.dart';
import 'meal_plan.dart';
import 'meal_plan_list_service.dart';

class PlanFormResult {
  final String name;
  final Color color;

  const PlanFormResult({required this.name, required this.color});
}

class PlanFormDialog extends StatefulWidget {
  final MealPlan? existingPlan;
  final MealPlanListService? mealPlanListService;
  final LimitsService? limitsService;

  const PlanFormDialog({
    super.key,
    this.existingPlan,
    this.mealPlanListService,
    this.limitsService,
  });

  @override
  State<PlanFormDialog> createState() => _PlanFormDialogState();
}

class _PlanFormDialogState extends State<PlanFormDialog> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _nameController;
  Color? _selectedColor;
  String? _colorError;

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController(text: widget.existingPlan?.name);
    _selectedColor = widget.existingPlan?.color;
    if (widget.existingPlan == null) {
      widget.mealPlanListService?.loadPlanUsage();
    }
  }

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  bool get _isEditMode => widget.existingPlan?.id != null;

  void _handleSave() {
    setState(() {
      _colorError = _selectedColor == null ? 'Please select a color' : null;
    });

    if (!_formKey.currentState!.validate() || _selectedColor == null) {
      return;
    }

    final name = _nameController.text.trim();
    Navigator.of(
      context,
    ).pop(PlanFormResult(name: name, color: _selectedColor!));
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return AlertDialog(
      title: Text(_isEditMode ? 'Edit Plan' : 'Create Plan'),
      content: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              TextFormField(
                controller: _nameController,
                autofocus: true,
                decoration: const InputDecoration(
                  labelText: 'Plan Name',
                  hintText: 'Enter plan name',
                ),
                validator: (value) {
                  if (value == null || value.trim().isEmpty) {
                    return 'Please enter a plan name';
                  }
                  return null;
                },
              ),
              const SizedBox(height: AppSpacing.medium),
              Text('Color', style: theme.textTheme.titleSmall),
              const SizedBox(height: AppSpacing.small),
              PlanColorPicker(
                selectedColor: _selectedColor,
                onColorSelected: (color) {
                  setState(() {
                    _selectedColor = color;
                    _colorError = null;
                  });
                },
              ),
              if (_colorError != null) ...[
                const SizedBox(height: AppSpacing.extraSmall),
                Text(
                  _colorError!,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.error,
                  ),
                ),
              ],
              if (!_isEditMode && widget.mealPlanListService != null) ...[
                const SizedBox(height: AppSpacing.small),
                LimitGate(
                  usage: widget.mealPlanListService!.planUsage,
                  cap: widget.limitsService?.capFor(LimitResources.mealPlan),
                  builder: (context, usage, cap) {
                    if (usage == null || cap == null) {
                      return const SizedBox.shrink();
                    }
                    return LimitCounter(
                      used: usage.used,
                      limit: cap.limit,
                      resetsInSeconds: usage.resetsInSeconds,
                      noun: 'plans',
                    );
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
        if (!_isEditMode && widget.mealPlanListService != null)
          LimitGate(
            usage: widget.mealPlanListService!.planUsage,
            cap: widget.limitsService?.capFor(LimitResources.mealPlan),
            builder: (context, usage, cap) {
              final blocked =
                  usage != null && cap != null && usage.used >= cap.limit;
              return FilledButton(
                onPressed: blocked ? null : _handleSave,
                child: const Text('Create'),
              );
            },
          )
        else
          FilledButton(
            onPressed: _handleSave,
            child: Text(_isEditMode ? 'Save' : 'Create'),
          ),
      ],
    );
  }
}
