import 'dart:typed_data';

import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../entities/event.dart';
import '../entities/attendee.dart';
import '../entities/rsvp.dart';
import '../entities/invitation.dart';

abstract class EventRepository {
  Future<Either<Failure, Event>> createEvent({
    required String title,
    String? description,
    EventDetails? details,
    EventSettings? settings,
  });
  
  Future<Either<Failure, Event>> updateEvent({
    required String eventId,
    String? title,
    String? description,
    EventDetails? details,
    EventSettings? settings,
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

  Future<Either<Failure, String>> uploadEventCover({
    required String eventId,
    required Uint8List bytes,
    required String fileName,
    required String mimeType,
  });
}
