import '../../domain/entities/invitation.dart';

/// `Invitation` del contrato (Ola 1), con `inviteLinkId` para las que llegan
/// por enlace.
class InvitationModel extends Invitation {
  const InvitationModel({
    required super.id,
    required super.eventId,
    required super.inviterId,
    required super.inviteeId,
    required super.status,
    super.message,
    super.inviteLinkId,
    super.sentAt,
    super.expiresAt,
    super.respondedAt,
  });

  factory InvitationModel.fromJson(Map<String, dynamic> json) {
    return InvitationModel(
      id: json['id'] ?? '',
      eventId: json['eventId'] ?? '',
      inviterId: json['inviterId'] ?? '',
      inviteeId: json['inviteeId'] ?? '',
      status: _parseStatus(json['status']),
      message: json['message'],
      inviteLinkId: json['inviteLinkId'],
      sentAt: _parseDate(json['sentAt']),
      expiresAt: _parseDate(json['expiresAt']),
      respondedAt: _parseDate(json['respondedAt']),
    );
  }

  static DateTime? _parseDate(dynamic value) =>
      value is String ? DateTime.parse(value).toLocal() : null;

  static InvitationStatus _parseStatus(String? status) {
    switch (status) {
      case 'ACCEPTED':
        return InvitationStatus.accepted;
      case 'DECLINED':
        return InvitationStatus.declined;
      case 'EXPIRED':
        return InvitationStatus.expired;
      default:
        return InvitationStatus.pending;
    }
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'eventId': eventId,
      'inviterId': inviterId,
      'inviteeId': inviteeId,
      'status': status.name.toUpperCase(),
      'message': message,
      'inviteLinkId': inviteLinkId,
      'sentAt': sentAt?.toUtc().toIso8601String(),
      'expiresAt': expiresAt?.toUtc().toIso8601String(),
      'respondedAt': respondedAt?.toUtc().toIso8601String(),
    };
  }
}
