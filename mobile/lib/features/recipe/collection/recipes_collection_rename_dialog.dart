import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class RecipesCollectionRenameDialog extends StatefulWidget {
  final String currentName;

  const RecipesCollectionRenameDialog({super.key, required this.currentName});

  @override
  State<RecipesCollectionRenameDialog> createState() =>
      _RecipesCollectionRenameDialogState();
}

class _RecipesCollectionRenameDialogState
    extends State<RecipesCollectionRenameDialog> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.currentName);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Rename recipes collection'),
      content: TextField(
        controller: _controller,
        decoration: const InputDecoration(labelText: 'Recipes collection name'),
        autofocus: true,
      ),
      actions: [
        TextButton(
          child: const Text('Cancel'),
          onPressed: () => context.pop(null),
        ),
        TextButton(
          child: const Text('Rename'),
          onPressed: () {
            final newName = _controller.text.trim();
            if (newName.isNotEmpty) {
              context.pop(newName);
            }
          },
        ),
      ],
    );
  }
}
