import 'package:flutter/material.dart';

import '../../core/theme.dart';
import 'recipe_detail.dart';
import 'recipe_image_fullscreen_viewer.dart';

class RecipeImageCarousel extends StatefulWidget {
  final List<RecipeImage> images;

  const RecipeImageCarousel({super.key, required this.images});

  @override
  State<RecipeImageCarousel> createState() => _RecipeImageCarouselState();
}

class _RecipeImageCarouselState extends State<RecipeImageCarousel> {
  late PageController _pageController;
  int _currentPage = 0;

  @override
  void initState() {
    super.initState();
    _pageController = PageController();
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  void _openFullScreenViewer(RecipeImage image) {
    showDialog(
      context: context,
      builder: (context) => RecipeImageFullscreenViewer(image: image),
      useSafeArea: false,
    );
  }

  Widget _buildImagePage(RecipeImage image) {
    final theme = Theme.of(context);
    return GestureDetector(
      onTap: () => _openFullScreenViewer(image),
      child: Container(
        color: theme.colorScheme.surfaceContainer,
        child: Image.network(
          image.url,
          fit: BoxFit.contain,
          loadingBuilder: (context, child, loadingProgress) {
            if (loadingProgress == null) return child;
            return Center(
              child: CircularProgressIndicator(
                value: loadingProgress.expectedTotalBytes != null
                    ? loadingProgress.cumulativeBytesLoaded /
                          loadingProgress.expectedTotalBytes!
                    : null,
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
      ),
    );
  }

  Widget _buildPaginationDots() {
    // Hide dots if only one image
    if (widget.images.length <= 1) {
      return const SizedBox.shrink();
    }

    return Positioned(
      bottom: AppSpacing.medium,
      left: 0,
      right: 0,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: List.generate(
          widget.images.length,
          (index) => Container(
            width: 8.0,
            height: 8.0,
            margin: const EdgeInsets.symmetric(horizontal: 4.0),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: _currentPage == index
                  ? Colors.white
                  : Colors.white.withValues(alpha: 0.4),
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AspectRatio(
      aspectRatio: 1.0,
      child: Stack(
        children: [
          PageView.builder(
            controller: _pageController,
            itemCount: widget.images.length,
            onPageChanged: (int page) {
              setState(() {
                _currentPage = page;
              });
            },
            itemBuilder: (context, index) {
              return _buildImagePage(widget.images[index]);
            },
          ),
          _buildPaginationDots(),
        ],
      ),
    );
  }
}
