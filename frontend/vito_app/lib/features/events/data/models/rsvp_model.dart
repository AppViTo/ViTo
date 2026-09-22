import '../../domain/entities/rsvp.dart';

class RSVPModel extends RSVP {
  const RSVPModel({
    required super.id,
    required super.eventId,
    required super.userId,
    required super.status,
    super.message,
    super.guestsCount,
    super.respondedAt,
    super.checkedInAt,
    super.checkInQrCode,
  });
  
  factory RSVPModel.fromJson(Map<String, dynamic> json) {
    return RSVPModel(
      id: json['id'] ?? '',
      eventId: json['eventId'] ?? '',
      userId: json['userId'] ?? '',
      status: _parseStatus(json['status']),
      message: json['message'],
      guestsCount: json['guestsCount'] ?? 0,
      respondedAt: json['respondedAt'] != null 
          ? DateTime.parse(json['respondedAt']) 
          : null,
      checkedInAt: json['checkedInAt'] != null 
          ? DateTime.parse(json['checkedInAt']) 
          : null,
      checkInQrCode: json['checkInQrCode'],
    );
  }
  
  static RSVPStatus _parseStatus(String? status) {
    switch (status) {
      case 'ACCEPTED':
        return RSVPStatus.accepted;
      case 'DECLINED':
        return RSVPStatus.declined;
      case 'MAYBE':
        return RSVPStatus.maybe;
      case 'WAITLISTED':
        return RSVPStatus.waitlisted;
      default:
        return RSVPStatus.pending;
    }
  }
  
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'eventId': eventId,
      'userId': userId,
      'status': status.name.toUpperCase(),
      'message': message,
      'guestsCount': guestsCount,
      'respondedAt': respondedAt?.toIso8601String(),
      'checkedInAt': checkedInAt?.toIso8601String(),
      'checkInQrCode': checkInQrCode,
    };
  }
}
