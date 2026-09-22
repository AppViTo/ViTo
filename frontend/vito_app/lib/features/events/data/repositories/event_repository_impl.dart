import 'package:dartz/dartz.dart';

import '../../../../core/error/exceptions.dart';
import '../../../../core/error/failures.dart';
import '../datasources/event_remote_datasource.dart';
import '../models/event_model.dart';
import '../../domain/entities/event.dart';
import '../../domain/entities/rsvp.dart';
import '../../domain/entities/invitation.dart';
import '../../domain/repositories/event_repository.dart';

class EventRepositoryImpl implements EventRepository {
  final EventRemoteDataSource remoteDataSource;
  
  EventRepositoryImpl({required this.remoteDataSource});
  
  @override
  Future<Either<Failure, Event>> createEvent({
    required String title,
    String? description,
    EventDetails? details,
    EventSettings? settings,
  }) async {
    try {
      final event = await remoteDataSource.createEvent(
        title: title,
        description: description,
        details: details != null ? (details as EventDetailsModel).toJson() : null,
        settings: settings != null ? (settings as EventSettingsModel).toJson() : null,
      );
      return Right(event);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, Event>> updateEvent({
    required String eventId,
    String? title,
    String? description,
    EventDetails? details,
    EventSettings? settings,
  }) async {
    try {
      final event = await remoteDataSource.updateEvent(
        eventId: eventId,
        title: title,
        description: description,
        details: details != null ? (details as EventDetailsModel).toJson() : null,
        settings: settings != null ? (settings as EventSettingsModel).toJson() : null,
      );
      return Right(event);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, void>> cancelEvent(String eventId) async {
    try {
      await remoteDataSource.cancelEvent(eventId);
      return const Right(null);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, Event>> getEvent(String eventId) async {
    try {
      final event = await remoteDataSource.getEvent(eventId);
      return Right(event);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, List<Event>>> getMyEvents({
    int page = 0,
    int size = 20,
  }) async {
    try {
      final events = await remoteDataSource.getMyEvents(page: page, size: size);
      return Right(events);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, List<Event>>> getUpcomingEvents({
    int page = 0,
    int size = 20,
  }) async {
    try {
      final events = await remoteDataSource.getUpcomingEvents(page: page, size: size);
      return Right(events);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, RSVP>> respondToRSVP({
    required String eventId,
    required RSVPStatus status,
    String? message,
    int guestsCount = 0,
  }) async {
    try {
      final rsvp = await remoteDataSource.respondToRSVP(
        eventId: eventId,
        status: status.name.toUpperCase(),
        message: message,
        guestsCount: guestsCount,
      );
      return Right(rsvp);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, List<RSVP>>> getEventAttendees({
    required String eventId,
    int page = 0,
    int size = 20,
  }) async {
    try {
      final attendees = await remoteDataSource.getEventAttendees(
        eventId: eventId,
        page: page,
        size: size,
      );
      return Right(attendees);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, Invitation>> sendInvitation({
    required String eventId,
    required String inviteeId,
    String? message,
  }) async {
    try {
      final invitation = await remoteDataSource.sendInvitation(
        eventId: eventId,
        inviteeId: inviteeId,
        message: message,
      );
      return Right(invitation);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, List<Invitation>>> getEventInvitations({
    required String eventId,
    int page = 0,
    int size = 20,
  }) async {
    try {
      final invitations = await remoteDataSource.getEventInvitations(
        eventId: eventId,
        page: page,
        size: size,
      );
      return Right(invitations);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, void>> respondToInvitation({
    required String eventId,
    required InvitationStatus status,
  }) async {
    try {
      await remoteDataSource.respondToInvitation(
        eventId: eventId,
        status: status.name.toUpperCase(),
      );
      return const Right(null);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, RSVP>> checkIn({
    required String eventId,
    required String qrCode,
  }) async {
    try {
      final rsvp = await remoteDataSource.checkIn(
        eventId: eventId,
        qrCode: qrCode,
      );
      return Right(rsvp);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, String>> generateCheckInQR(String eventId) async {
    try {
      final qrCode = await remoteDataSource.generateCheckInQR(eventId);
      return Right(qrCode);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
}
