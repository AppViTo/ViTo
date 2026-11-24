import 'dart:typed_data';

import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../entities/event.dart';
import '../entities/attendee.dart';
import '../entities/rsvp.dart';
import '../entities/invitation.dart';
import '../entities/invite_link.dart';

abstract class EventRepository {
  Future<Either<Failure, Event>> createEvent({
    required String title,
    String? description,
    EventDetails? details,
    EventSettings? settings,
  });
  
  /// Envía solo lo que cambió de [original] a [edited]. Si nada cambió,
  /// devuelve [original] sin llamar al backend. Un cupo menor que los
  /// confirmados o un evento no vigente responden [ConflictFailure].
  Future<Either<Failure, Event>> updateEvent({
    required Event original,
    required Event edited,
  });
  
  Future<Either<Failure, void>> cancelEvent(String eventId);
  
  Future<Either<Failure, Event>> getEvent(String eventId);
  
  Future<Either<Failure, List<Event>>> getMyEvents({
    int page = 0,
    int size = 20,
  });
  
  Future<Either<Failure, List<Event>>> getUpcomingEvents({
    int page = 0,
    int size = 20,
  });
  
  /// `status` solo puede ser `accepted`, `declined` o `maybe`. Un `accepted`
  /// puede volver como `waitlisted` si el cupo está lleno.
  Future<Either<Failure, RSVP>> respondToRSVP({
    required String eventId,
    required RSVPStatus status,
    String? message,
  });

  Future<Either<Failure, List<Attendee>>> getEventAttendees({
    required String eventId,
    int page = 0,
    int size = 20,
  });
  
  Future<Either<Failure, Invitation>> sendInvitation({
    required String eventId,
    required String inviteeId,
    String? message,
  });
  
  Future<Either<Failure, List<Invitation>>> getEventInvitations({
    required String eventId,
    int page = 0,
    int size = 20,
  });
  
  Future<Either<Failure, RSVP>> checkIn({
    required String eventId,
    required String qrCode,
  });
  
  Future<Either<Failure, String>> generateCheckInQR(String eventId);

  /// `scope=invited`: eventos a los que invitaron a quien consulta.
  Future<Either<Failure, List<Event>>> getInvitedEvents({
    int page = 0,
    int size = 20,
  });

  Future<Either<Failure, InviteLink>> createInviteLink({
    required String eventId,
    int? expiresInDays,
    int? maxUses,
  });

  Future<Either<Failure, List<InviteLink>>> listInviteLinks({
    required String eventId,
    int page = 0,
    int size = 20,
  });

  Future<Either<Failure, void>> revokeInviteLink({
    required String eventId,
    required String linkId,
  });

  /// Un token inexistente, vencido o revocado responde [NotFoundFailure].
  Future<Either<Failure, InviteLinkPreview>> getInviteLinkPreview(String token);

  /// `409` (sin usos o evento inactivo) responde [ConflictFailure].
  Future<Either<Failure, Invitation>> acceptInviteLink(String token);

  Future<Either<Failure, String>> uploadEventCover({
    required String eventId,
    required Uint8List bytes,
    required String fileName,
    required String mimeType,
  });
}
