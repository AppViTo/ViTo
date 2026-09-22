import '../../domain/entities/invitation.dart';

class InvitationModel extends Invitation {
  const InvitationModel({
    required super.id,
    required super.eventId,
    required super.inviterId,
    required super.inviteeId,
    required super.status,
    super.message,
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
      sentAt: json['sentAt'] != null 
          ? DateTime.parse(json['sentAt']) 
          : null,
      expiresAt: json['expiresAt'] != null 
          ? DateTime.parse(json['expiresAt']) 
          : null,
      respondedAt: json['respondedAt'] != null 
          ? DateTime.parse(json['respondedAt']) 
          : null,
    );
  }
  
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
      'sentAt': sentAt?.toIso8601String(),
      'expiresAt': expiresAt?.toIso8601String(),
      'respondedAt': respondedAt?.toIso8601String(),
    };
  }
}
