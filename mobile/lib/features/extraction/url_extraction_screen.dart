import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../core/routes.dart';
import '../../core/theme.dart';
import '../../shared/extensions.dart';
import '../../shared/loading_widget.dart';
import '../limits/limit_cap.dart';
import '../limits/limit_counter.dart';
import '../limits/limit_gate.dart';
import '../limits/limits_service.dart';
import '../recipe/initial_recipe_form_data.dart';
import 'extraction_service.dart';
import 'web_recipe_extractor.dart';

class UrlExtractionScreen extends StatefulWidget {
  final ExtractionService extractionService;
  final LimitsService limitsService;
  final String? initialUrl;

  const UrlExtractionScreen({
    super.key,
    required this.extractionService,
    required this.limitsService,
    this.initialUrl,
  });

  @override
  State<UrlExtractionScreen> createState() => _UrlExtractionScreenState();
}

class _UrlExtractionScreenState extends State<UrlExtractionScreen> {
  late final WebViewController _controller;
  final TextEditingController _urlController = TextEditingController();
  bool _isLoading = false;
  bool _isExtracting = false;
  String? _errorMessage;
  bool _isCurrentInputUrl = false;

  @override
  void initState() {
    super.initState();
    widget.extractionService.loadExtractionUsage();
    _initializeWebView();

    final initialUrl = widget.initialUrl;
    if (initialUrl != null) {
      _urlController.text = initialUrl;
      _isCurrentInputUrl = initialUrl.isUrl;
    }

    _urlController.addListener(() {
      final newValue = _urlController.text.trim().isUrl;
      if (newValue != _isCurrentInputUrl) {
        setState(() {
          _isCurrentInputUrl = newValue;
        });
      }
    });

    if (initialUrl != null) {
      _loadUrlInternal(initialUrl);
    }
  }

  void _initializeWebView() {
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(
        NavigationDelegate(
          onNavigationRequest: (request) {
            final uri = Uri.parse(request.url);
            if (uri.scheme == 'http' || uri.scheme == 'https') {
              return NavigationDecision.navigate;
            }
            return NavigationDecision.prevent;
          },
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
    final input = _urlController.text.trim();
    if (input.isEmpty) {
      _showSnackBar('Please enter a URL or search terms');
      return;
    }
    FocusScope.of(context).unfocus();
    _loadUrlInternal(input);
  }

  void _loadUrlInternal(String input) {
    final String urlToLoad;

    if (input.isUrl) {
      if (!input.startsWith('http://') && !input.startsWith('https://')) {
        urlToLoad = 'https://$input';
      } else {
        urlToLoad = input;
      }
    } else {
      final encodedQuery = Uri.encodeQueryComponent(input);
      urlToLoad = 'https://www.google.com/search?q=$encodedQuery';
    }

    try {
      _controller.loadRequest(Uri.parse(urlToLoad));
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
      // Capture the current URL from WebView
      final currentUrl = await _controller.currentUrl();

      // Extract HTML content from WebView
      final htmlContent = await WebRecipeExtractor.extractHtmlContent(
        _controller,
      );

      if (htmlContent.isEmpty) {
        throw Exception('No content found on the page');
      }

      // Extract recipe using API
      final extractedRecipe = await widget.extractionService.extractFromText(
        htmlContent,
      );

      // Show success message
      _showSnackBar('Recipe extracted successfully!');

      // Navigate to create recipe screen with extracted data and source URL
      if (mounted) {
        final formData = InitialRecipeFormData(
          recipeDetail: extractedRecipe.toRecipeDetail(),
          sourceUrl: currentUrl,
        );

        context.goNamed(AppRoute.recipeCreate.name, extra: formData);
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

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, result) async {
        if (didPop) return;
        if (await _controller.canGoBack()) {
          final urlBeforeBack = await _controller.currentUrl();
          await _controller.goBack();
          // Detect redirect loops (e.g. Google search result redirects):
          // if the URL bounces back to the same page, exit the screen.
          // Dirty hack but works
          await Future.delayed(const Duration(milliseconds: 300));
          final urlAfterBack = await _controller.currentUrl();
          if (urlAfterBack == urlBeforeBack && context.mounted) {
            Navigator.of(context).pop();
          }
        } else {
          if (context.mounted) {
            Navigator.of(context).pop();
          }
        }
      },
      child: Scaffold(
        appBar: AppBar(
          leading: IconButton(
            icon: const Icon(Icons.arrow_back),
            onPressed: () => Navigator.of(context).pop(),
          ),
          title: const Text('Extract Recipe'),
          backgroundColor: theme.colorScheme.inversePrimary,
        ),
        body: SafeArea(
          top: false,
          child: Column(
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
                              hintText: 'Enter recipe URL or search terms',
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
                          child: Text(_isCurrentInputUrl ? 'Load' : 'Search'),
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
                    LimitGate(
                      usage: widget.extractionService.extractionUsage,
                      cap: widget.limitsService.capFor(
                        LimitResources.extraction,
                      ),
                      builder: (context, usage, cap) {
                        if (usage == null || cap == null) {
                          return const SizedBox.shrink();
                        }
                        return Padding(
                          padding: const EdgeInsets.only(top: AppSpacing.small),
                          child: LimitCounter(
                            used: usage.used,
                            limit: cap.limit,
                            resetsInSeconds: usage.resetsInSeconds,
                            noun: 'extractions',
                          ),
                        );
                      },
                    ),
                  ],
                ),
              ),

              // WebView Section
              Expanded(
                child: Stack(
                  children: [
                    WebViewWidget(controller: _controller),
                    if (_isLoading)
                      const Positioned.fill(child: LoadingWidget()),
                  ],
                ),
              ),
            ],
          ),
        ),
        floatingActionButton: LimitGate(
          usage: widget.extractionService.extractionUsage,
          cap: widget.limitsService.capFor(LimitResources.extraction),
          builder: (context, usage, cap) {
            final blocked =
                usage != null && cap != null && usage.used >= cap.limit;
            return FloatingActionButton.extended(
              onPressed: (_isExtracting || blocked) ? null : _extractRecipe,
              icon: _isExtracting
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.download),
              label: Text(_isExtracting ? 'Extracting...' : 'Extract Recipe'),
            );
          },
        ),
      ),
    );
  }
}
