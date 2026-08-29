import 'invite_resource_type.dart';

class Invite {
  final String id;
  final InviteResourceType resourceType;
  final String label;
  final String invitedBy;

  const Invite({
    required this.id,
    required this.resourceType,
    required this.label,
    required this.invitedBy,
  });

  factory Invite.fromJson(Map<String, dynamic> json) {
    return Invite(
      id: json['id'] as String,
      resourceType: InviteResourceType.fromApiString(
        json['resourceType'] as String,
      ),
      label: json['label'] as String,
      invitedBy: json['invitedBy'] as String,
    );
  }
}
