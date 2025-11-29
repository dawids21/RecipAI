import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../core/widgets/sharing_dialog.dart';
import '../../../shared/api_error_widget.dart';
import '../../../shared/loading_widget.dart';
import 'recipes_collection.dart';
import 'recipes_collection_list_item.dart';
import 'recipes_collection_list_service.dart';

class RecipesCollectionListScreen extends StatefulWidget {
  final RecipesCollectionListService recipesCollectionListService;

  const RecipesCollectionListScreen({
    super.key,
    required this.recipesCollectionListService,
  });

  @override
  State<RecipesCollectionListScreen> createState() =>
      _RecipesCollectionListScreenState();
}

class _RecipesCollectionListScreenState
    extends State<RecipesCollectionListScreen> {
  Future<void> _handleRefresh() async {
    await widget.recipesCollectionListService.loadRecipesCollections();
  }

  Future<void> _handleCreate() async {
    final nameController = TextEditingController();

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Create recipes collection'),
        content: TextField(
          controller: nameController,
          decoration: const InputDecoration(
            labelText: 'Recipes collection name',
          ),
          autofocus: true,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Create'),
          ),
        ],
      ),
    );

    if (confirmed == true && nameController.text.isNotEmpty) {
      try {
        await widget.recipesCollectionListService.createRecipesCollection(
          nameController.text.trim(),
        );
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Recipes collection created')),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text('Failed to create: $e')));
        }
      }
    }
  }

  Future<void> _handleRename(String id, String newName) async {
    try {
      await widget.recipesCollectionListService.updateRecipesCollection(
        id,
        newName,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Recipes collection renamed')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Failed to rename: $e')));
      }
    }
  }

  Future<void> _handleDelete(String id, String name) async {
    final theme = Theme.of(context);

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete recipes collection'),
        content: Text(
          'Are you sure you want to delete \'$name\'? This action cannot be undone.',
        ),
        actions: [
          TextButton(
            onPressed: () => context.pop(false),
            child: const Text('Cancel'),
          ),
          TextButton(
            style: TextButton.styleFrom(
              foregroundColor: theme.colorScheme.error,
            ),
            onPressed: () => context.pop(true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    try {
      await widget.recipesCollectionListService.deleteRecipesCollection(id);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Recipes collection deleted')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Failed to delete: $e')));
      }
    }
  }

  Future<void> _showSharingDialog(RecipesCollection collection) async {
    widget.recipesCollectionListService.loadSharedUsers(collection.id);

    if (!mounted) return;

    await showDialog(
      context: context,
      builder: (context) => SharingDialog(
        title: 'Share ${collection.name}',
        sharedUsers: widget.recipesCollectionListService.sharedUsers,
        onShare: (email) async {
          try {
            await widget.recipesCollectionListService.shareCollection(
              collection.id,
              email,
            );
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text('Collection shared with $email')),
              );
            }
          } catch (e) {
            if (mounted) {
              ScaffoldMessenger.of(
                context,
              ).showSnackBar(SnackBar(content: Text('Failed to share: $e')));
            }
            rethrow;
          }
        },
        onUnshare: (email) async {
          try {
            await widget.recipesCollectionListService.unshareCollection(
              collection.id,
              email,
            );
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text('Collection unshared with $email')),
              );
            }
          } catch (e) {
            if (mounted) {
              ScaffoldMessenger.of(
                context,
              ).showSnackBar(SnackBar(content: Text('Failed to unshare: $e')));
            }
            rethrow;
          }
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Recipes Collections'),
        backgroundColor: theme.colorScheme.inversePrimary,
      ),
      body: RefreshIndicator(
        onRefresh: _handleRefresh,
        child: ValueListenableBuilder(
          valueListenable:
              widget.recipesCollectionListService.recipesCollections,
          builder: (context, asyncValueRecipesCollections, child) {
            return asyncValueRecipesCollections.when(
              loading: () => const LoadingWidget(),
              data: (recipesCollections) {
                if (recipesCollections.isEmpty) {
                  return Center(
                    child: Text(
                      'No recipes collections found',
                      style: theme.textTheme.labelMedium,
                    ),
                  );
                }
                return ListView.builder(
                  itemCount: recipesCollections.length,
                  itemBuilder: (context, index) {
                    final recipesCollection = recipesCollections[index];
                    return RecipesCollectionListItem(
                      recipesCollection: recipesCollection,
                      onRename: (newName) =>
                          _handleRename(recipesCollection.id, newName),
                      onShare: () => _showSharingDialog(recipesCollection),
                      onDelete: () => _handleDelete(
                        recipesCollection.id,
                        recipesCollection.name,
                      ),
                    );
                  },
                );
              },
              error: (error) => ApiErrorWidget(
                errorMessage: 'Error: $error',
                onRetry:
                    widget.recipesCollectionListService.loadRecipesCollections,
              ),
            );
          },
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _handleCreate,
        child: const Icon(Icons.add),
      ),
    );
  }
}
