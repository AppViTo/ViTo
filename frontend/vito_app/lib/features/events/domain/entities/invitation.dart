import 'package:equatable/equatable.dart';

class Invitation extends Equatable {
  final String id;
  final String eventId;
  final String inviterId;
  final String inviteeId;
  final InvitationStatus status;
  final String? message;

  /// Enlace por el que llegó el invitado; `null` si se invitó desde la app.
  final String? inviteLinkId;
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
    this.inviteLinkId,
    this.sentAt,
    this.expiresAt,
    this.respondedAt,
  });

  bool get isExpired => expiresAt != null && DateTime.now().isAfter(expiresAt!);

  bool get viaInviteLink => inviteLinkId != null;

  @override
  List<Object?> get props => [
        id,
        eventId,
        inviterId,
        inviteeId,
        status,
        message,
        inviteLinkId,
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
