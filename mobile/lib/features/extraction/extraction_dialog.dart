import 'package:flutter/material.dart';

enum ExtractionMethod { url, image }

class ExtractionDialog extends StatelessWidget {
  const ExtractionDialog({super.key});

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Extract Recipe'),
      content: const Text('Choose your extraction method:'),
      actions: [
        TextButton.icon(
          onPressed: () => Navigator.of(context).pop(ExtractionMethod.url),
          icon: const Icon(Icons.link),
          label: const Text('Extract from URL'),
        ),
        TextButton.icon(
          onPressed: () => Navigator.of(context).pop(ExtractionMethod.image),
          icon: const Icon(Icons.photo_camera),
          label: const Text('Extract from Image'),
        ),
      ],
    );
  }
}
