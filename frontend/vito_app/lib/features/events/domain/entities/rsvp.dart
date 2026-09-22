import 'package:equatable/equatable.dart';

class RSVP extends Equatable {
  final String id;
  final String eventId;
  final String userId;
  final RSVPStatus status;
  final String? message;
  final int guestsCount;
  final DateTime? respondedAt;
  final DateTime? checkedInAt;
  final String? checkInQrCode;
  
  const RSVP({
    required this.id,
    required this.eventId,
    required this.userId,
    required this.status,
    this.message,
    this.guestsCount = 0,
    this.respondedAt,
    this.checkedInAt,
    this.checkInQrCode,
  });
  
  bool get isCheckedIn => checkedInAt != null;
  
  @override
  List<Object?> get props => [
        id,
        eventId,
        userId,
        status,
        message,
        guestsCount,
        respondedAt,
        checkedInAt,
        checkInQrCode,
      ];
}

enum RSVPStatus {
  pending,
  accepted,
  declined,
  maybe,
  waitlisted,
}
