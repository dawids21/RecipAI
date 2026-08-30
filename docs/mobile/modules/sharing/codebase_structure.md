# Sharing — Codebase Structure

```
mobile/lib/features/sharing/
├── resource_permission.dart      # ResourcePermission model (email, role, pending) with fromJson
├── share_refused_exception.dart  # ShareRefusedReason enum and ShareRefusedException, parsed from a 409 body
└── sharing_dialog.dart           # The generic SharingDialog all four resource types open
```

```
mobile/test/features/sharing/
└── sharing_dialog_widget_test.dart   # SharingDialog pumped directly with a plain ValueNotifier and callback spies
```
