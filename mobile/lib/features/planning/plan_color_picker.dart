import 'package:flutter/material.dart';

import '../../core/theme.dart';

class PlanColorPicker extends StatelessWidget {
  final Color? selectedColor;
  final ValueChanged<Color> onColorSelected;

  static const List<Color> predefinedColors = [
    Color(0xFFE57373), // Red
    Color(0xFFBA68C8), // Purple
    Color(0xFF64B5F6), // Blue
    Color(0xFF4FC3F7), // Light Blue
    Color(0xFF4DD0E1), // Cyan
    Color(0xFF4DB6AC), // Teal
    Color(0xFF81C784), // Green
    Color(0xFFAED581), // Light Green
    Color(0xFFFFD54F), // Amber
    Color(0xFFFFB74D), // Orange
    Color(0xFFFF8A65), // Deep Orange
    Color(0xFFA1887F), // Brown
  ];

  const PlanColorPicker({
    super.key,
    required this.selectedColor,
    required this.onColorSelected,
  });

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: AppSpacing.small,
      runSpacing: AppSpacing.small,
      children: predefinedColors
          .map((color) => _buildColorButton(color))
          .toList(),
    );
  }

  Widget _buildColorButton(Color color) {
    final isSelected = selectedColor?.toARGB32() == color.toARGB32();

    final iconColor = color.computeLuminance() > 0.5
        ? Colors.black
        : Colors.white;

    return InkWell(
      onTap: () => onColorSelected(color),
      borderRadius: BorderRadius.circular(32),
      child: Container(
        width: 64,
        height: 64,
        decoration: BoxDecoration(
          color: color,
          shape: BoxShape.circle,
          border: Border.all(color: Colors.grey, width: 1),
        ),
        child: isSelected
            ? Icon(Icons.check, color: iconColor, size: 32)
            : null,
      ),
    );
  }
}
