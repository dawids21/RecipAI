import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../core/theme.dart';

class RecipeSearchBar extends StatefulWidget {
  final ValueListenable<String> searchQuery;
  final void Function(String) onSearchChanged;

  const RecipeSearchBar({
    super.key,
    required this.searchQuery,
    required this.onSearchChanged,
  });

  @override
  State<RecipeSearchBar> createState() => _RecipeSearchBarState();
}

class _RecipeSearchBarState extends State<RecipeSearchBar> {
  late final TextEditingController _controller;
  late final FocusNode _focusNode;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.searchQuery.value);
    _focusNode = FocusNode();
    widget.searchQuery.addListener(_updateControllerFromValue);
  }

  @override
  void dispose() {
    widget.searchQuery.removeListener(_updateControllerFromValue);
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  void _updateControllerFromValue() {
    if (_controller.text != widget.searchQuery.value) {
      _controller.text = widget.searchQuery.value;
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      height: 64.0,
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.medium,
        vertical: AppSpacing.small,
      ),
      child: ValueListenableBuilder<String>(
        valueListenable: widget.searchQuery,
        builder: (context, query, _) {
          return TextField(
            controller: _controller,
            focusNode: _focusNode,
            decoration: InputDecoration(
              hintText: 'Search recipes...',
              prefixIcon: const Icon(Icons.search),
              suffixIcon: query.isNotEmpty
                  ? IconButton(
                      icon: const Icon(Icons.clear),
                      onPressed: () {
                        widget.onSearchChanged('');
                        _focusNode.unfocus();
                      },
                    )
                  : null,
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8.0),
                borderSide: BorderSide(
                  color: theme.colorScheme.outline.withValues(alpha: 0.5),
                ),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8.0),
                borderSide: BorderSide(
                  color: theme.colorScheme.outline.withValues(alpha: 0.5),
                ),
              ),
              filled: true,
              fillColor: theme.colorScheme.surfaceContainer,
              contentPadding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.medium,
                vertical: AppSpacing.small,
              ),
            ),
            onChanged: widget.onSearchChanged,
            onTapOutside: (_) => _focusNode.unfocus(),
          );
        },
      ),
    );
  }
}
