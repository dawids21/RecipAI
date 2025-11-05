import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class ShoppingListRenameDialog extends StatefulWidget {
  final String currentName;

  const ShoppingListRenameDialog({super.key, required this.currentName});

  @override
  State<ShoppingListRenameDialog> createState() =>
      _ShoppingListRenameDialogState();
}

class _ShoppingListRenameDialogState extends State<ShoppingListRenameDialog> {
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
      title: const Text('Rename Shopping List'),
      content: TextField(
        controller: _controller,
        decoration: const InputDecoration(
          labelText: 'List Name',
          hintText: 'Enter new name',
        ),
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
