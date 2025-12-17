import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/theme.dart';

class SourceLinkWidget extends StatelessWidget {
  final String sourceUrl;

  const SourceLinkWidget({super.key, required this.sourceUrl});

  String _extractDomain(String url) {
    try {
      final uri = Uri.parse(url);
      return uri.host;
    } catch (e) {
      return url;
    }
  }

  Future<void> _launchUrl(BuildContext context) async {
    try {
      final uri = Uri.parse(sourceUrl);

      if (!await canLaunchUrl(uri)) {
        if (context.mounted) {
          _showError(context, 'Cannot open this URL');
        }
        return;
      }

      await launchUrl(uri);
    } catch (e) {
      if (context.mounted) {
        _showError(context, 'Failed to open URL: ${e.toString()}');
      }
    }
  }

  void _showError(BuildContext context, String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message), duration: const Duration(seconds: 3)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final domain = _extractDomain(sourceUrl);

    return InkWell(
      onTap: () => _launchUrl(context),
      borderRadius: BorderRadius.circular(4.0),
      child: Row(
        children: [
          Text(
            domain,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.primary,
              decoration: TextDecoration.underline,
            ),
          ),
          const SizedBox(width: AppSpacing.extraSmall),
          Icon(Icons.open_in_new, size: 14, color: theme.colorScheme.primary),
        ],
      ),
    );
  }
}
