import 'package:webview_flutter/webview_flutter.dart';

class WebRecipeExtractor {
  static Future<String> extractHtmlContent(WebViewController controller) async {
    try {
      final result = await controller.runJavaScriptReturningResult(
        'document.body.innerText',
      );

      // Convert the result to string and handle potential quotes
      String htmlContent = result.toString();

      // Remove surrounding quotes if present (JavaScript result might be wrapped in quotes)
      if (htmlContent.startsWith('"') && htmlContent.endsWith('"')) {
        htmlContent = htmlContent.substring(1, htmlContent.length - 1);
      }

      return htmlContent;
    } catch (e) {
      throw Exception('Failed to extract HTML content: $e');
    }
  }
}
