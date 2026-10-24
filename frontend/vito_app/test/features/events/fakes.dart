import 'dart:typed_data';

import 'package:dartz/dartz.dart';

import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/features/events/data/datasources/event_remote_datasource.dart';
import 'package:vito_app/features/events/data/datasources/media_remote_datasource.dart';
import 'package:vito_app/features/events/data/models/event_model.dart';
import 'package:vito_app/features/events/data/models/invitation_model.dart';
import 'package:vito_app/features/events/data/models/rsvp_model.dart';
import 'package:vito_app/features/events/domain/entities/event.dart';
import 'package:vito_app/features/events/domain/entities/invitation.dart';
import 'package:vito_app/features/events/domain/entities/rsvp.dart';
import 'package:vito_app/features/events/domain/repositories/event_repository.dart';

class CreatedEventCall {
  final String title;
  final String? description;
  final EventDetails? details;
  final EventSettings? settings;

  const CreatedEventCall({
    required this.title,
    required this.description,
    required this.details,
    required this.settings,
  });
}

class FakeEventRepository implements EventRepository {
  final List<CreatedEventCall> created = [];
  final List<(String, String, String)> uploadedCovers = [];
  final bool failCreate;
  final bool failCoverUpload;

  FakeEventRepository({this.failCreate = false, this.failCoverUpload = false});

  @override
  Future<Either<Failure, Event>> createEvent({
    required String title,
    String? description,
    EventDetails? details,
    EventSettings? settings,
  }) async {
    created.add(CreatedEventCall(
      title: title,
      description: description,
      details: details,
      settings: settings,
    ));
    if (failCreate) {
      return const Left(ServerFailure(message: 'Sin backend disponible'));
    }
    return Right(Event(
      id: 'evt_1',
      organizerId: 'usr_1',
      title: title,
      description: description,
      details: details,
      settings: settings,
    ));
  }

  @override
  Future<Either<Failure, String>> uploadEventCover({
    required String eventId,
    required Uint8List bytes,
    required String fileName,
    required String mimeType,
  }) async {
    if (failCoverUpload) {
      return const Left(NetworkFailure(message: 'Sin conexión'));
    }
    uploadedCovers.add((eventId, fileName, mimeType));
    return const Right('https://cdn.vito.app/cover.jpg');
  }

  @override
  Future<Either<Failure, Event>> updateEvent({
    required String eventId,
    String? title,
    String? description,
    EventDetails? details,
    EventSettings? settings,
  }) async =>
      const Left(ServerFailure());

  @override
  Future<Either<Failure, void>> cancelEvent(String eventId) async =>
      const Left(ServerFailure());

  @override
  Future<Either<Failure, Event>> getEvent(String eventId) async =>
      const Left(ServerFailure());

  @override
  Future<Either<Failure, List<Event>>> getMyEvents({
    int page = 0,
    int size = 20,
  }) async =>
      const Left(ServerFailure());

  @override
  Future<Either<Failure, List<Event>>> getUpcomingEvents({
    int page = 0,
    int size = 20,
  }) async =>
      const Left(ServerFailure());

  @override
  Future<Either<Failure, RSVP>> respondToRSVP({
    required String eventId,
    required RSVPStatus status,
    String? message,
    int guestsCount = 0,
  }) async =>
      const Left(ServerFailure());

  @override
  Future<Either<Failure, List<RSVP>>> getEventAttendees({
    required String eventId,
    int page = 0,
    int size = 20,
  }) async =>
      const Left(ServerFailure());

  @override
  Future<Either<Failure, Invitation>> sendInvitation({
    required String eventId,
    required String inviteeId,
    String? message,
  }) async =>
      const Left(ServerFailure());

  @override
  Future<Either<Failure, List<Invitation>>> getEventInvitations({
    required String eventId,
    int page = 0,
    int size = 20,
  }) async =>
      const Left(ServerFailure());

  @override
  Future<Either<Failure, void>> respondToInvitation({
    required String eventId,
    required InvitationStatus status,
  }) async =>
      const Left(ServerFailure());

  @override
  Future<Either<Failure, RSVP>> checkIn({
    required String eventId,
    required String qrCode,
  }) async =>
      const Left(ServerFailure());

  @override
  Future<Either<Failure, String>> generateCheckInQR(String eventId) async =>
      const Left(ServerFailure());
}

class FakeEventRemoteDataSource implements EventRemoteDataSource {
  final Exception? createError;
  Map<String, dynamic>? capturedDetails;
  Map<String, dynamic>? capturedSettings;
  String? capturedTitle;

  FakeEventRemoteDataSource({this.createError});

  @override
  Future<EventModel> createEvent({
    required String title,
    String? description,
    Map<String, dynamic>? details,
    Map<String, dynamic>? settings,
  }) async {
    capturedTitle = title;
    capturedDetails = details;
    capturedSettings = settings;
    if (createError != null) throw createError!;
    return EventModel.fromJson({
      'id': 'evt_1',
      'organizerId': 'usr_1',
      'title': title,
      'description': description,
      'details': details,
      'settings': settings,
    });
  }

  @override
  Future<EventModel> updateEvent({
    required String eventId,
    String? title,
    String? description,
    Map<String, dynamic>? details,
    Map<String, dynamic>? settings,
  }) =>
      throw UnimplementedError();

  @override
  Future<void> cancelEvent(String eventId) => throw UnimplementedError();

  @override
  Future<EventModel> getEvent(String eventId) => throw UnimplementedError();

  @override
  Future<List<EventModel>> getMyEvents({int page = 0, int size = 20}) =>
      throw UnimplementedError();

  @override
  Future<List<EventModel>> getUpcomingEvents({int page = 0, int size = 20}) =>
      throw UnimplementedError();

  @override
  Future<RSVPModel> respondToRSVP({
    required String eventId,
    required String status,
    String? message,
    int guestsCount = 0,
  }) =>
      throw UnimplementedError();

  @override
  Future<List<RSVPModel>> getEventAttendees({
    required String eventId,
    int page = 0,
    int size = 20,
  }) =>
      throw UnimplementedError();

  @override
  Future<InvitationModel> sendInvitation({
    required String eventId,
    required String inviteeId,
    String? message,
  }) =>
      throw UnimplementedError();

  @override
  Future<List<InvitationModel>> getEventInvitations({
    required String eventId,
    int page = 0,
    int size = 20,
  }) =>
      throw UnimplementedError();

  @override
  Future<void> respondToInvitation({
    required String eventId,
    required String status,
  }) =>
      throw UnimplementedError();

  @override
  Future<RSVPModel> checkIn({
    required String eventId,
    required String qrCode,
  }) =>
      throw UnimplementedError();

  @override
  Future<String> generateCheckInQR(String eventId) =>
      throw UnimplementedError();
}

class FakeMediaRemoteDataSource implements MediaRemoteDataSource {
  final Exception? uploadError;
  String? capturedEventId;
  String? capturedFileName;
  String? capturedMimeType;
  Uint8List? capturedBytes;

  FakeMediaRemoteDataSource({this.uploadError});

  @override
  Future<String> uploadEventCover({
    required String eventId,
    required Uint8List bytes,
    required String fileName,
    required String mimeType,
  }) async {
    capturedEventId = eventId;
    capturedBytes = bytes;
    capturedFileName = fileName;
    capturedMimeType = mimeType;
    if (uploadError != null) throw uploadError!;
    return 'https://cdn.vito.app/cover.jpg';
  }

  @override
  Future<String> uploadMedia({
    required String eventId,
    required Uint8List bytes,
    required String fileName,
    required String mimeType,
  }) async {
    capturedEventId = eventId;
    capturedBytes = bytes;
    capturedFileName = fileName;
    capturedMimeType = mimeType;
    if (uploadError != null) throw uploadError!;
    return 'med_1';
  }
}
