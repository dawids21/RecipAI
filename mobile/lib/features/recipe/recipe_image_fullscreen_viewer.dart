import 'package:flutter/material.dart';
import 'package:photo_view/photo_view.dart';

import '../../core/theme.dart';
import 'recipe_detail.dart';

class RecipeImageFullscreenViewer extends StatelessWidget {
  final RecipeImage image;

  const RecipeImageFullscreenViewer({super.key, required this.image});

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: Colors.transparent,
      insetPadding: EdgeInsets.zero,
      child: Theme(
        data: ThemeData.dark(),
        child: Scaffold(
          backgroundColor: Colors.transparent,
          body: Stack(
            children: [
              PhotoView(
                imageProvider: NetworkImage(image.url),
                minScale: PhotoViewComputedScale.contained * 1.0,
                maxScale: PhotoViewComputedScale.covered * 2.0,
                backgroundDecoration: const BoxDecoration(
                  color: Colors.transparent,
                ),
                onTapUp: (context, details, controllerValue) {
                  Navigator.of(context).pop();
                },
                loadingBuilder: (context, event) {
                  return Center(
                    child: CircularProgressIndicator(
                      value: event == null || event.expectedTotalBytes == null
                          ? null
                          : event.cumulativeBytesLoaded /
                                event.expectedTotalBytes!,
                      color: Colors.white,
                    ),
                  );
                },
                errorBuilder: (context, error, stackTrace) {
                  return Container(
                    color: Colors.grey.shade800,
                    child: Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.broken_image,
                            size: 64,
                            color: Colors.grey.shade400,
                          ),
                          const SizedBox(height: AppSpacing.small),
                          Text(
                            'Failed to load image',
                            style: TextStyle(color: Colors.grey.shade400),
                          ),
                        ],
                      ),
                    ),
                  );
                },
              ),
              Positioned(
                top: AppSpacing.large,
                right: AppSpacing.medium,
                child: SafeArea(
                  child: IconButton(
                    icon: const Icon(
                      Icons.close,
                      color: Colors.white,
                      size: 32,
                    ),
                    onPressed: () => Navigator.of(context).pop(),
                    tooltip: 'Close',
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
