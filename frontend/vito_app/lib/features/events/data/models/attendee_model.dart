import '../../domain/entities/attendee.dart';
import 'rsvp_model.dart';

/// `Attendee` del contrato: `userId`, `displayName` (nullable), `status` y
/// `respondedAt`.
class AttendeeModel extends Attendee {
  const AttendeeModel({
    required super.userId,
    required super.status,
    super.displayName,
    super.respondedAt,
  });

  factory AttendeeModel.fromJson(Map<String, dynamic> json) {
    final respondedAt = json['respondedAt'];
    return AttendeeModel(
      userId: json['userId'] ?? '',
      displayName: json['displayName'],
      status: parseRsvpStatus(json['status']),
      respondedAt:
          respondedAt is String ? DateTime.parse(respondedAt).toLocal() : null,
    );
  }

  Map<String, dynamic> toJson() => {
        'userId': userId,
        'displayName': displayName,
        'status': rsvpStatusToJson(status),
        'respondedAt': respondedAt?.toUtc().toIso8601String(),
      };
}
