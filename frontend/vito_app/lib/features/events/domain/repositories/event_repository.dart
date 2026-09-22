import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../entities/event.dart';
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
  
  Future<Either<Failure, RSVP>> respondToRSVP({
    required String eventId,
    required RSVPStatus status,
    String? message,
    int guestsCount = 0,
  });
  
  Future<Either<Failure, List<RSVP>>> getEventAttendees({
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
  
  Future<Either<Failure, void>> respondToInvitation({
    required String eventId,
    required InvitationStatus status,
  });
  
  Future<Either<Failure, RSVP>> checkIn({
    required String eventId,
    required String qrCode,
  });
  
  Future<Either<Failure, String>> generateCheckInQR(String eventId);
}
