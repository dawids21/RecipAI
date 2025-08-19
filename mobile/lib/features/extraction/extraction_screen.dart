import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../core/api_service.dart';
import '../../core/routes.dart';
import '../../core/theme.dart';
import '../../shared/loading_widget.dart';
import 'web_recipe_extractor.dart';

class ExtractionScreen extends StatefulWidget {
  const ExtractionScreen({super.key});

  @override
  State<ExtractionScreen> createState() => _ExtractionScreenState();
}

class _ExtractionScreenState extends State<ExtractionScreen> {
  late final WebViewController _controller;
  final TextEditingController _urlController = TextEditingController();
  bool _isLoading = false;
  bool _isExtracting = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _initializeWebView();
  }

  void _initializeWebView() {
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(
        NavigationDelegate(
          onProgress: (progress) {
            setState(() {
              _isLoading = progress < 100;
            });
          },
          onPageStarted: (url) {
            setState(() {
              _isLoading = true;
              _errorMessage = null;
            });
          },
          onPageFinished: (url) {
            setState(() {
              _isLoading = false;
            });
          },
          onWebResourceError: (error) {
            setState(() {
              _isLoading = false;
              _errorMessage = 'Failed to load page: ${error.description}';
            });
          },
        ),
      );
  }

  void _loadUrl() {
    final url = _urlController.text.trim();
    if (url.isEmpty) {
      _showSnackBar('Please enter a URL');
      return;
    }

    // Add https:// if no protocol is specified
    String formattedUrl = url;
    if (!url.startsWith('http://') && !url.startsWith('https://')) {
      formattedUrl = 'https://$url';
    }

    try {
      _controller.loadRequest(Uri.parse(formattedUrl));
      // Hide keyboard
      FocusScope.of(context).unfocus();
    } catch (e) {
      _showSnackBar('Invalid URL format');
    }
  }

  Future<void> _extractRecipe() async {
    if (_isExtracting) return;

    setState(() {
      _isExtracting = true;
    });

    try {
      // Extract HTML content from WebView
      final htmlContent = await WebRecipeExtractor.extractHtmlContent(
        _controller,
      );

      if (htmlContent.isEmpty) {
        throw Exception('No content found on the page');
      }

      // Extract recipe using API
      final extractedRecipe = await ApiService.extractRecipeFromText(
        htmlContent,
      );

      // Show success message
      _showSnackBar('Recipe extracted successfully!');

      // Navigate to create recipe screen with extracted data
      if (mounted) {
        context.goNamed(
          AppRoute.recipeCreate.name,
          extra: extractedRecipe.toRecipeDetail(),
        );
      }
    } catch (e) {
      _showSnackBar('Failed to extract recipe: ${e.toString()}');
    } finally {
      if (mounted) {
        setState(() {
          _isExtracting = false;
        });
      }
    }
  }

  void _showSnackBar(String message) {
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(message), duration: const Duration(seconds: 3)),
      );
    }
  }

  @override
  void dispose() {
    _urlController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Extract Recipe'),
        backgroundColor: theme.colorScheme.inversePrimary,
      ),
      body: Column(
        children: [
          // URL Input Section
          Container(
            padding: AppSpacing.screenPadding,
            child: Column(
              children: [
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _urlController,
                        decoration: const InputDecoration(
                          hintText: 'Enter recipe URL',
                          border: OutlineInputBorder(),
                          prefixIcon: Icon(Icons.link),
                        ),
                        keyboardType: TextInputType.url,
                        textInputAction: TextInputAction.go,
                        onSubmitted: (_) => _loadUrl(),
                      ),
                    ),
                    const SizedBox(width: AppSpacing.small),
                    ElevatedButton(
                      onPressed: _loadUrl,
                      child: const Text('Load'),
                    ),
                  ],
                ),
                if (_errorMessage != null) ...[
                  const SizedBox(height: AppSpacing.small),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.errorContainer,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      _errorMessage!,
                      style: TextStyle(
                        color: theme.colorScheme.onErrorContainer,
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),

          // WebView Section
          Expanded(
            child: Stack(
              children: [
                WebViewWidget(controller: _controller),
                if (_isLoading) const Positioned.fill(child: LoadingWidget()),
              ],
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _isExtracting ? null : _extractRecipe,
        icon: _isExtracting
            ? const SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : const Icon(Icons.download),
        label: Text(_isExtracting ? 'Extracting...' : 'Extract Recipe'),
      ),
    );
  }
}
