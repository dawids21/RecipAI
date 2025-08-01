## FEATURE:

Remove hardcoded layout values in the UI code and replace them with either theme-based values or constants defined in
the `theme.dart` file.
This will ensure consistency across the app and make it easier to maintain and update styles in the future.

## EXAMPLES:

Instead of:

```dart
@override
Widget build(BuildContext context) {
  return Text('No recipes found', style: TextStyle(fontSize: 18));
}
```

use:

```dart
@override
Widget build(BuildContext context) {
  const theme = Theme.of(context);
  return Text('No recipes found', style: theme.textTheme.labelMedium);
}
```

## DOCUMENTATION:

- https://m3.material.io - Material Design 3 documentation, design guidelines
- https://m3.material.io/styles/typography/applying-type - where to use which text styles

## OTHER CONSIDERATIONS:

- Try to use theme-based values (`Theme.of(context)`).
- If not possible, use custom constants defined in `theme.dart`.
