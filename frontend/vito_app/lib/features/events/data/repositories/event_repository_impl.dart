import 'package:dartz/dartz.dart';
import 'dart:typed_data';

import '../../../../core/error/exceptions.dart';
import '../../../../core/error/failures.dart';
import '../datasources/event_remote_datasource.dart';
import '../datasources/media_remote_datasource.dart';
import '../models/event_model.dart';
import '../models/event_patch.dart';
import '../../domain/entities/event.dart';
import '../../domain/entities/attendee.dart';
import '../../domain/entities/rsvp.dart';
import '../../domain/entities/invitation.dart';
import '../../domain/entities/invite_link.dart';
import '../../domain/repositories/event_repository.dart';

class EventRepositoryImpl implements EventRepository {
  final EventRemoteDataSource remoteDataSource;
  final MediaRemoteDataSource mediaDataSource;

  EventRepositoryImpl({
    required this.remoteDataSource,
    required this.mediaDataSource,
  });

  Failure _toFailure(Object error) {
    if (error is ServerException) {
      // Los enlaces de invitación distinguen 404 (no existe o ya no está
      // disponible) y 409 (sin usos o evento inactivo).
      switch (error.statusCode) {
        case 403:
          return AuthorizationFailure(message: error.message);
        case 404:
          return NotFoundFailure(message: error.message);
        case 409:
          return ConflictFailure(message: error.message);
      }
      return ServerFailure(message: error.message);
    }
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
    required Event original,
    required Event edited,
  }) async {
    final patch = eventPatchJson(original, edited);
    if (patch.isEmpty) return Right(original);
    try {
      final event = await remoteDataSource.updateEvent(
        eventId: original.id,
        patch: patch,
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
  }) async {
    try {
      final rsvp = await remoteDataSource.respondToRSVP(
        eventId: eventId,
        status: status,
        message: message,
      );
      return Right(rsvp);
    } catch (e) {
      return Left(_toFailure(e));
    }
  }
  
  @override
  Future<Either<Failure, List<Attendee>>> getEventAttendees({
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

  @override
  Future<Either<Failure, List<Event>>> getInvitedEvents({
    int page = 0,
    int size = 20,
  }) =>
      _guard(() => remoteDataSource.getInvitedEvents(page: page, size: size));

  @override
  Future<Either<Failure, InviteLink>> createInviteLink({
    required String eventId,
    int? expiresInDays,
    int? maxUses,
  }) =>
      _guard(() => remoteDataSource.createInviteLink(
            eventId: eventId,
            expiresInDays: expiresInDays,
            maxUses: maxUses,
          ));

  @override
  Future<Either<Failure, List<InviteLink>>> listInviteLinks({
    required String eventId,
    int page = 0,
    int size = 20,
  }) =>
      _guard(() => remoteDataSource.listInviteLinks(
            eventId: eventId,
            page: page,
            size: size,
          ));

  @override
  Future<Either<Failure, void>> revokeInviteLink({
    required String eventId,
    required String linkId,
  }) =>
      _guard(() => remoteDataSource.revokeInviteLink(
            eventId: eventId,
            linkId: linkId,
          ));

  @override
  Future<Either<Failure, InviteLinkPreview>> getInviteLinkPreview(
    String token,
  ) =>
      _guard(() => remoteDataSource.getInviteLinkPreview(token));

  @override
  Future<Either<Failure, Invitation>> acceptInviteLink(String token) =>
      _guard(() => remoteDataSource.acceptInviteLink(token));

  Future<Either<Failure, T>> _guard<T>(Future<T> Function() call) async {
    try {
      return Right(await call());
    } catch (e) {
      return Left(_toFailure(e));
    }
  }
}
