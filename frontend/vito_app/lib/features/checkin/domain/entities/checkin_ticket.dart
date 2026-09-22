import 'package:equatable/equatable.dart';

class CheckInTicket extends Equatable {
  final String id;
  final String eventId;
  final String userId;
  final String qrCode;
  final CheckInStatus status;
  final DateTime? createdAt;
  final DateTime? expiresAt;
  final DateTime? checkedInAt;
  final String? checkedInLocation;
  
  const CheckInTicket({
    required this.id,
    required this.eventId,
    required this.userId,
    required this.qrCode,
    required this.status,
    this.createdAt,
    this.expiresAt,
    this.checkedInAt,
    this.checkedInLocation,
  });
  
  bool get isValid => status == CheckInStatus.active && 
      (expiresAt == null || DateTime.now().isBefore(expiresAt!));
  
  @override
  List<Object?> get props => [
        id,
        eventId,
        userId,
        qrCode,
        status,
        createdAt,
        expiresAt,
        checkedInAt,
        checkedInLocation,
      ];
}

enum CheckInStatus {
  pending,
  active,
  used,
  expired,
  revoked,
}
