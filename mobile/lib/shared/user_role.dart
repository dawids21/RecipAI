enum UserRole {
  owner,
  editor;

  String toApiString() {
    switch (this) {
      case UserRole.owner:
        return 'OWNER';
      case UserRole.editor:
        return 'EDITOR';
    }
  }

  static UserRole fromApiString(String apiString) {
    switch (apiString.toUpperCase()) {
      case 'OWNER':
        return UserRole.owner;
      case 'EDITOR':
        return UserRole.editor;
      default:
        throw ArgumentError('Unknown user role: $apiString');
    }
  }

  String get displayName {
    switch (this) {
      case UserRole.owner:
        return 'Owner';
      case UserRole.editor:
        return 'Editor';
    }
  }
}
