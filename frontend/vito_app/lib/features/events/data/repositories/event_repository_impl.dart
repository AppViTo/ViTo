import 'package:dartz/dartz.dart';
import 'dart:typed_data';

import '../../../../core/error/exceptions.dart';
import '../../../../core/error/failures.dart';
import '../datasources/event_remote_datasource.dart';
import '../datasources/media_remote_datasource.dart';
import '../models/event_model.dart';
import '../../domain/entities/event.dart';
import '../../domain/entities/rsvp.dart';
import '../../domain/entities/invitation.dart';
import '../../domain/repositories/event_repository.dart';

class EventRepositoryImpl implements EventRepository {
  final EventRemoteDataSource remoteDataSource;
  final MediaRemoteDataSource mediaDataSource;

  EventRepositoryImpl({
    required this.remoteDataSource,
    required this.mediaDataSource,
  });

  Failure _toFailure(Object error) {
    if (error is ServerException) return ServerFailure(message: error.message);
    if (error is ValidationException) {
      return ValidationFailure(message: error.message);
    }
    if (error is AuthenticationException) {
      return AuthenticationFailure(message: error.message);
    }
    if (error is AuthorizationException) {
      return AuthorizationFailure(message: error.message);
    }
    if (error is NetworkException) return NetworkFailure(message: error.message);
    return ServerFailure(message: error.toString());
  }

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
        details:
            details != null ? EventDetailsModel.fromEntity(details).toJson() : null,
        settings:
            settings != null ? EventSettingsModel.fromEntity(settings).toJson() : null,
      );
      return Right(event);
    } catch (e) {
      return Left(_toFailure(e));
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
        details:
            details != null ? EventDetailsModel.fromEntity(details).toJson() : null,
        settings:
            settings != null ? EventSettingsModel.fromEntity(settings).toJson() : null,
      );
      return Right(event);
    } catch (e) {
      return Left(_toFailure(e));
    }
  }
  
  @override
  Future<Either<Failure, void>> cancelEvent(String eventId) async {
    try {
      await remoteDataSource.cancelEvent(eventId);
      return const Right(null);
    } catch (e) {
      return Left(_toFailure(e));
    }
  }
  
  @override
  Future<Either<Failure, Event>> getEvent(String eventId) async {
    try {
      final event = await remoteDataSource.getEvent(eventId);
      return Right(event);
    } catch (e) {
      return Left(_toFailure(e));
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
    } catch (e) {
      return Left(_toFailure(e));
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
    } catch (e) {
      return Left(_toFailure(e));
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
    } catch (e) {
      return Left(_toFailure(e));
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
    } catch (e) {
      return Left(_toFailure(e));
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
    } catch (e) {
      return Left(_toFailure(e));
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
    } catch (e) {
      return Left(_toFailure(e));
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
    } catch (e) {
      return Left(_toFailure(e));
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
    } catch (e) {
      return Left(_toFailure(e));
    }
  }
  
  @override
  Future<Either<Failure, String>> generateCheckInQR(String eventId) async {
    try {
      final qrCode = await remoteDataSource.generateCheckInQR(eventId);
      return Right(qrCode);
    } catch (e) {
      return Left(_toFailure(e));
    }
  }

  @override
  Future<Either<Failure, String>> uploadEventCover({
    required String eventId,
    required Uint8List bytes,
    required String fileName,
    required String mimeType,
  }) async {
    try {
      final url = await mediaDataSource.uploadEventCover(
        eventId: eventId,
        bytes: bytes,
        fileName: fileName,
        mimeType: mimeType,
      );
      return Right(url);
    } catch (e) {
      return Left(_toFailure(e));
    }
  }
}
