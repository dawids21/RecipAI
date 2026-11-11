# Sharing Module

The sharer's side of sharing: one generic `SharingDialog` that recipes, collections, shopping lists and meal plans
all open to invite users and to remove access or cancel an invite. It holds no service; each resource feature
supplies the permission list and the share/unshare calls. The invitee's side is `invites`.

## Boundary

- **Backend endpoints**: none of its own. Each resource feature calls its own `permissions`, `share` and `unshare`
  endpoints, whose shared shapes and 409 refusal are in `docs/backend/modules/permissions/api.md`.
- **Uses**: no other feature.
- **Used by**: `recipe` (recipes, collections), `shopping_list` and `planning`; the call sites are in `ui.md` →
  Call sites. Their repositories parse permission rows into `ResourcePermission` and a 409 from `share` into
  `ShareRefusedException`.

## File Map

```
mobile/lib/features/sharing/
├── resource_permission.dart      # ResourcePermission model (email, role, pending)
├── share_refused_exception.dart  # ShareRefusedReason and ShareRefusedException, parsed from a 409 body
└── sharing_dialog.dart           # The generic SharingDialog
```

```
mobile/test/features/sharing/
└── sharing_dialog_widget_test.dart   # Pumps the dialog directly with a plain ValueNotifier and callback spies
```

## Services and State

None. The dialog takes the permission list as a listenable plus `onShare`/`onUnshare` callbacks, so the state lives
in the resource feature's service, which reloads the list after a successful share or unshare.
