import 'package:equatable/equatable.dart';

/// Respuesta de un usuario a un evento (`Rsvp` del contrato, Ola 1).
///
/// Sin QR ni `guestsCount`: el contrato no los tiene. Un `ACCEPTED` puede
/// volver como `WAITLISTED` si el cupo está lleno; entonces llega
/// [waitlistedAt].
class RSVP extends Equatable {
  final String eventId;
  final String userId;
  final RSVPStatus status;
  final String? message;
  final DateTime? respondedAt;
  final DateTime? waitlistedAt;

  const RSVP({
    required this.eventId,
    required this.userId,
    required this.status,
    this.message,
    this.respondedAt,
    this.waitlistedAt,
  });

  bool get isWaitlisted => status == RSVPStatus.waitlisted;

  @override
  List<Object?> get props => [
        eventId,
        userId,
        status,
        message,
        respondedAt,
        waitlistedAt,
      ];
}

enum RSVPStatus {
  pending,
  accepted,
  declined,
  maybe,
  waitlisted,
}

/// Estados que se pueden enviar en `RespondRsvpRequest`. `PENDING` y
/// `WAITLISTED` solo los asigna el backend.
const respondableRsvpStatuses = {
  RSVPStatus.accepted,
  RSVPStatus.declined,
  RSVPStatus.maybe,
};
