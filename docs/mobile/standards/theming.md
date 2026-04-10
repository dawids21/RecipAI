# Mobile Theming & Styling Standards

## Theme Configuration

Material Design 3, configured in `core/theme.dart`.

- **Color Scheme**: Generated from `Colors.deepOrange` seed color
- **Applied in**: `main.dart` as `theme: AppTheme.theme`

## Theme Access in Widgets

Use `final theme = Theme.of(context);` at the beginning of `build` methods for consistent theme access.

```dart
@override
Widget build(BuildContext context) {
  final theme = Theme.of(context);
  return Text('Hello', style: theme.textTheme.bodyLarge);
}
```

## Choosing Values

Follow this priority order:

1. **`Theme.of(context)`** — always prefer theme values (e.g., `theme.textTheme.bodyLarge`, `theme.colorScheme.primary`)
2. **`AppSpacing` / `AppAnimations` constants** from `core/theme.dart` — when `Theme.of()` doesn't provide a suitable value
3. **New constants in `core/theme.dart`** — only if the value is generic enough to be reused across multiple widgets; follow the Material Design 3 8dp grid system
4. **Hardcoded values** — acceptable when a value is very specific to a single widget and not reused elsewhere

## AppSpacing Constants

Defined in `core/theme.dart`:

| Constant | Value | Use |
|---|---|---|
| `screenPadding` | `EdgeInsets.all(16.0)` | Standard screen padding |
| `cardMargin` | `EdgeInsets.symmetric(horizontal: 16.0, vertical: 4.0)` | Card margins |
| `listTilePadding` | `EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0)` | ListTile content padding |
| `smallVertical` | `EdgeInsets.symmetric(vertical: 4.0)` | Small vertical spacing |
| `mediumVertical` | `EdgeInsets.symmetric(vertical: 8.0)` | Medium vertical spacing |
| `extraSmall` | `4dp` | Extra small spacing |
| `small` | `8dp` | Small spacing |
| `medium` | `16dp` | Medium spacing |
| `large` | `24dp` | Large spacing |
| `extraLarge` | `32dp` | Extra large spacing |

## AppAnimations Constants

| Constant | Value | Use |
|---|---|---|
| `sectionTransition` | `Duration(milliseconds: 300)` | Section expand/collapse animations |
| `sectionCurve` | `Curves.easeInOut` | Smooth section transition curve |