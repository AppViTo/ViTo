import '../../domain/entities/checkin_ticket.dart';

class CheckInTicketModel extends CheckInTicket {
  const CheckInTicketModel({
    required super.id,
    required super.eventId,
    required super.userId,
    required super.qrCode,
    required super.status,
    super.createdAt,
    super.expiresAt,
    super.checkedInAt,
    super.checkedInLocation,
  });
  
  factory CheckInTicketModel.fromJson(Map<String, dynamic> json) {
    return CheckInTicketModel(
      id: json['id'] ?? '',
      eventId: json['eventId'] ?? '',
      userId: json['userId'] ?? '',
      qrCode: json['qrCode'] ?? '',
      status: _parseStatus(json['status']),
      createdAt: json['createdAt'] != null 
          ? DateTime.parse(json['createdAt']) 
          : null,
      expiresAt: json['expiresAt'] != null 
          ? DateTime.parse(json['expiresAt']) 
          : null,
      checkedInAt: json['checkedInAt'] != null 
          ? DateTime.parse(json['checkedInAt']) 
          : null,
      checkedInLocation: json['checkedInLocation'],
    );
  }
  
  static CheckInStatus _parseStatus(String? status) {
    switch (status) {
      case 'ACTIVE':
        return CheckInStatus.active;
      case 'USED':
        return CheckInStatus.used;
      case 'EXPIRED':
        return CheckInStatus.expired;
      case 'REVOKED':
        return CheckInStatus.revoked;
      default:
        return CheckInStatus.pending;
    }
  }
  
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'eventId': eventId,
      'userId': userId,
      'qrCode': qrCode,
      'status': status.name.toUpperCase(),
      'createdAt': createdAt?.toIso8601String(),
      'expiresAt': expiresAt?.toIso8601String(),
      'checkedInAt': checkedInAt?.toIso8601String(),
      'checkedInLocation': checkedInLocation,
    };
  }
}
