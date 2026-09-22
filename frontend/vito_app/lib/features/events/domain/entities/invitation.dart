import 'package:equatable/equatable.dart';

class Invitation extends Equatable {
  final String id;
  final String eventId;
  final String inviterId;
  final String inviteeId;
  final InvitationStatus status;
  final String? message;
  final DateTime? sentAt;
  final DateTime? expiresAt;
  final DateTime? respondedAt;
  
  const Invitation({
    required this.id,
    required this.eventId,
    required this.inviterId,
    required this.inviteeId,
    required this.status,
    this.message,
    this.sentAt,
    this.expiresAt,
    this.respondedAt,
  });
  
  bool get isExpired => expiresAt != null && DateTime.now().isAfter(expiresAt!);
  
  @override
  List<Object?> get props => [
        id,
        eventId,
        inviterId,
        inviteeId,
        status,
        message,
        sentAt,
        expiresAt,
        respondedAt,
      ];
}

enum InvitationStatus {
  pending,
  accepted,
  declined,
  expired,
}
