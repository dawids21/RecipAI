# Invites — Codebase Structure

```
mobile/lib/features/invites/
├── invite_resource_type.dart   # InviteResourceType enum over the four resource keys, fromApiString/displayName
├── invite.dart                 # Invite model (id, resourceType, label, invitedBy) with fromJson
├── invites_repository.dart     # API communication layer — GET /invites, POST accept/decline; declares InviteGoneException
├── invites_service.dart        # Holds the session's pending invites; loads on MainScreen open and app resume; accept/decline fan out to the matching list service
├── invites_screen.dart         # The /invites screen: refresh, empty/error/loading states, decline confirmation, busy rows
├── invite_list_item.dart       # One row: type icon, label, sender, Decline/Accept buttons
└── invites_setup.dart          # Dependency injection setup for the invites module
```
