import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/theme.dart';
import 'recipe_detail.dart';
import 'recipe_image_input.dart';

class RecipeImageManager extends StatefulWidget {
  final List<RecipeImageInput> initialImages;
  final List<RecipeImage> existingImages; // For displaying thumbnails
  final ValueChanged<List<RecipeImageInput>> onImagesChanged;

  const RecipeImageManager({
    super.key,
    required this.initialImages,
    required this.existingImages,
    required this.onImagesChanged,
  });

  @override
  State<RecipeImageManager> createState() => _RecipeImageManagerState();
}

class _RecipeImageManagerState extends State<RecipeImageManager> {
  static const int maxImages = 2;
  static final int maxFileSizeBytes = 5 * 1024 * 1024; // 5MB
  final ImagePicker _imagePicker = ImagePicker();
  late final List<RecipeImageInput> _images = List.from(widget.initialImages);

  void _showImageSourceBottomSheet() {
    showModalBottomSheet(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.camera_alt),
              title: const Text('Camera'),
              onTap: () {
                Navigator.of(context).pop();
                _pickImage(ImageSource.camera);
              },
            ),
            ListTile(
              leading: const Icon(Icons.photo_library),
              title: const Text('Gallery'),
              onTap: () {
                Navigator.of(context).pop();
                _pickImage(ImageSource.gallery);
              },
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _pickImage(ImageSource source) async {
    try {
      final XFile? pickedFile = await _imagePicker.pickImage(
        source: source,
        imageQuality: 85,
        maxWidth: 1600,
      );
      if (pickedFile != null) {
        await _addImage(pickedFile);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Failed to pick image: $e')));
      }
    }
  }

  Future<void> _addImage(XFile file) async {
    final fileSize = await file.length();
    if (fileSize > maxFileSizeBytes) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Image too large. Maximum size is 5MB.'),
          ),
        );
      }
      return;
    }

    setState(() {
      _images.add(RecipeImageInput.newImage(file));
    });
    widget.onImagesChanged(_images);
  }

  void _removeImage(int index) {
    setState(() {
      _images.removeAt(index);
    });
    widget.onImagesChanged(_images);
  }

  void _reorderImages(int oldIndex, int newIndex) {
    setState(() {
      if (newIndex > oldIndex) {
        newIndex -= 1;
      }
      final item = _images.removeAt(oldIndex);
      _images.insert(newIndex, item);
    });
    widget.onImagesChanged(_images);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final canAddMore = _images.length < maxImages;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      spacing: AppSpacing.extraSmall,
      children: [
        SizedBox(
          height: 120,
          child: Row(
            children: [
              Expanded(
                child: ListView(
                  scrollDirection: Axis.horizontal,
                  children: [
                    ReorderableListView(
                      scrollDirection: Axis.horizontal,
                      shrinkWrap: true,
                      onReorder: _reorderImages,
                      buildDefaultDragHandles: false,
                      proxyDecorator: (child, index, animation) {
                        return AnimatedBuilder(
                          animation: animation,
                          builder: (context, child) {
                            return Material(
                              elevation: 0,
                              color: Colors.transparent,
                              child: child,
                            );
                          },
                          child: child,
                        );
                      },
                      children: [
                        for (int index = 0; index < _images.length; index++)
                          Padding(
                            key: ValueKey(_images[index].uuid),
                            padding: const EdgeInsets.only(
                              right: AppSpacing.small,
                            ),
                            child: ReorderableDragStartListener(
                              index: index,
                              child: _buildImageThumbnail(
                                _images[index],
                                index,
                              ),
                            ),
                          ),
                      ],
                    ),
                    // Small add button next to last image
                    if (canAddMore)
                      Container(
                        width: 120,
                        height: 120,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(color: theme.colorScheme.outline),
                        ),
                        child: Center(
                          child: IconButton(
                            onPressed: _showImageSourceBottomSheet,
                            icon: Icon(
                              Icons.add,
                              color: theme.colorScheme.primary,
                            ),
                            tooltip: 'Add image (${_images.length}/$maxImages)',
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
        if (!canAddMore)
          Row(
            spacing: AppSpacing.extraSmall,
            children: [
              Icon(
                Icons.info_outline,
                size: 16,
                color: theme.colorScheme.onSurfaceVariant,
              ),
              Text(
                'Maximum $maxImages images reached',
                style: theme.textTheme.labelSmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
      ],
    );
  }

  Widget _buildImageThumbnail(RecipeImageInput imageInput, int index) {
    final theme = Theme.of(context);

    return Stack(
      children: [
        Container(
          width: 120,
          height: 120,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: theme.colorScheme.outline),
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: imageInput.isNewImage
                ? Image.file(File(imageInput.file!.path), fit: BoxFit.cover)
                : imageInput.isExistingImage
                ? Image.network(
                    imageInput.url!,
                    fit: BoxFit.cover,
                    errorBuilder: (context, error, stackTrace) {
                      return Container(
                        color: theme.colorScheme.surfaceContainerHighest,
                        child: const Center(child: Icon(Icons.image)),
                      );
                    },
                  )
                : Container(
                    color: theme.colorScheme.surfaceContainerHighest,
                    child: const Center(child: Icon(Icons.image)),
                  ),
          ),
        ),
        Positioned(
          top: 4,
          right: 4,
          child: GestureDetector(
            onTap: () => _removeImage(index),
            child: Container(
              width: 24,
              height: 24,
              decoration: BoxDecoration(
                color: Colors.black54,
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.close, size: 16, color: Colors.white),
            ),
          ),
        ),
      ],
    );
  }
}
