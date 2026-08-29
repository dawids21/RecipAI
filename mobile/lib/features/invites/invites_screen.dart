import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import 'invite.dart';
import 'invite_list_item.dart';
import 'invites_repository.dart';
import 'invites_service.dart';

class InvitesScreen extends StatefulWidget {
  final InvitesService invitesService;

  const InvitesScreen({super.key, required this.invitesService});

  @override
  State<InvitesScreen> createState() => _InvitesScreenState();
}

class _InvitesScreenState extends State<InvitesScreen> {
  final Set<String> _busyIds = {};

  @override
  void initState() {
    super.initState();
    widget.invitesService.loadInvites();
  }

  Future<void> _handleAccept(Invite invite) {
    return _answer(
      invite,
      () => widget.invitesService.acceptInvite(invite),
      'Invite accepted',
    );
  }

  Future<void> _handleDecline(Invite invite) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Decline invite'),
        content: Text(
          'Are you sure you want to decline the invite to \'${invite.label}\' from ${invite.invitedBy}?',
        ),
        actions: [
          TextButton(
            onPressed: () => context.pop(false),
            child: const Text('Cancel'),
          ),
          TextButton(
            style: TextButton.styleFrom(
              foregroundColor: Theme.of(context).colorScheme.error,
            ),
            onPressed: () => context.pop(true),
            child: const Text('Decline'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    await _answer(
      invite,
      () => widget.invitesService.declineInvite(invite),
      'Invite declined',
    );
  }

  Future<void> _answer(
    Invite invite,
    Future<void> Function() call,
    String successMessage,
  ) async {
    final scaffoldMessenger = ScaffoldMessenger.of(context);
    setState(() => _busyIds.add(invite.id));
    try {
      await call();
      scaffoldMessenger.showSnackBar(SnackBar(content: Text(successMessage)));
    } on InviteGoneException {
      scaffoldMessenger.showSnackBar(
        const SnackBar(content: Text('That invite is no longer available')),
      );
    } catch (e) {
      scaffoldMessenger.showSnackBar(SnackBar(content: Text('Failed: $e')));
    } finally {
      if (mounted) {
        setState(() => _busyIds.remove(invite.id));
      }
    }
  }

  Widget _scrollable(Widget child) {
    return LayoutBuilder(
      builder: (context, constraints) {
        return ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [SizedBox(height: constraints.maxHeight, child: child)],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Invites'),
        backgroundColor: theme.colorScheme.inversePrimary,
      ),
      body: RefreshIndicator(
        onRefresh: widget.invitesService.loadInvites,
        child: ValueListenableBuilder(
          valueListenable: widget.invitesService.invites,
          builder: (context, asyncInvites, child) {
            return asyncInvites.when(
              loading: () => const LoadingWidget(),
              error: (error) => _scrollable(
                ApiErrorWidget(
                  errorMessage: 'Error: $error',
                  onRetry: widget.invitesService.loadInvites,
                ),
              ),
              data: (invites) {
                if (invites.isEmpty) {
                  return _scrollable(
                    Center(
                      child: Text(
                        'No pending invites',
                        style: theme.textTheme.labelMedium,
                      ),
                    ),
                  );
                }
                return ListView.builder(
                  itemCount: invites.length,
                  itemBuilder: (context, index) {
                    final invite = invites[index];
                    return InviteListItem(
                      invite: invite,
                      busy: _busyIds.contains(invite.id),
                      onAccept: () => _handleAccept(invite),
                      onDecline: () => _handleDecline(invite),
                    );
                  },
                );
              },
            );
          },
        ),
      ),
    );
  }
}
