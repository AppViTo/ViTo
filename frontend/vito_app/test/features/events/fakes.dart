import 'dart:typed_data';

import 'package:dartz/dartz.dart';

import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/features/events/data/datasources/event_remote_datasource.dart';
import 'package:vito_app/features/events/data/datasources/media_remote_datasource.dart';
import 'package:vito_app/features/events/data/models/attendee_model.dart';
import 'package:vito_app/features/events/data/models/event_model.dart';
import 'package:vito_app/features/events/data/models/invitation_model.dart';
import 'package:vito_app/features/events/data/models/invite_link_model.dart';
import 'package:vito_app/features/events/data/models/rsvp_model.dart';
import 'package:vito_app/features/events/domain/entities/attendee.dart';
import 'package:vito_app/features/events/domain/entities/event.dart';
import 'package:vito_app/features/events/domain/entities/invitation.dart';
import 'package:vito_app/features/events/domain/entities/invite_link.dart';
import 'package:vito_app/features/events/domain/entities/rsvp.dart';
import 'package:vito_app/features/events/domain/repositories/event_repository.dart';

/// Enlace de invitación de prueba, vigente salvo que se diga lo contrario.
InviteLink fakeInviteLink({
  String id = 'inl_1',
  String eventId = 'evt_1',
  String token = 'k3J9xQ2mV7pL1sT8wY4zBc',
  int uses = 0,
  int? maxUses,
  bool revoked = false,
  DateTime? expiresAt,
  DateTime? createdAt,
}) =>
    InviteLink(
      id: id,
      eventId: eventId,
      token: token,
      url: 'https://app.vitoevents.app/i/$token',
      expiresAt: expiresAt ?? DateTime.now().add(const Duration(days: 7)),
      maxUses: maxUses,
      uses: uses,
      revoked: revoked,
      createdAt: createdAt ?? DateTime.utc(2026, 11, 20, 12),
    );

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

  /// `null` responde `ServerFailure`; una lista, `Right` con esos eventos.
  final List<Event>? myEvents;
  final List<Event>? upcomingEvents;

  /// `null` responde `ServerFailure`; un evento, `Right` con ese detalle.
  final Event? eventDetail;

  /// Respuesta de `respondToRSVP`. Si es `null`, se usa [rsvpFailure] o, en
  /// su defecto, un `Rsvp` con el mismo `status` enviado.
  final RSVP? rsvpResponse;
  final Failure? rsvpFailure;
  final List<(String, RSVPStatus, String?)> rsvpCalls = [];

  /// Cuántas veces se pidió el detalle (para ver la recarga tras responder).
  int getEventCalls = 0;

  /// `scope=invited`. Por defecto vacío, para no cambiar las pruebas del
  /// home anteriores a la Ola 1; `null` responde `ServerFailure`.
  final List<Event>? invitedEvents;

  /// Enlaces de invitación (T041–T043).
  final List<InviteLink> inviteLinks;
  final Failure? inviteLinkFailure;
  final InviteLinkPreview? inviteLinkPreview;
  final Failure? previewFailure;
  final Failure? acceptFailure;
  final List<String> revokedLinkIds = [];
  final List<String> acceptedTokens = [];
  int createInviteLinkCalls = 0;

  FakeEventRepository({
    this.failCreate = false,
    this.failCoverUpload = false,
    this.myEvents,
    this.upcomingEvents,
    this.eventDetail,
    this.rsvpResponse,
    this.rsvpFailure,
    this.invitedEvents = const [],
    this.inviteLinks = const [],
    this.inviteLinkFailure,
    this.inviteLinkPreview,
    this.previewFailure,
    this.acceptFailure,
  });

  @override
  Future<Either<Failure, List<Event>>> getInvitedEvents({
    int page = 0,
    int size = 20,
  }) async =>
      invitedEvents != null ? Right(invitedEvents!) : const Left(ServerFailure());

  @override
  Future<Either<Failure, InviteLink>> createInviteLink({
    required String eventId,
    int? expiresInDays,
    int? maxUses,
  }) async {
    createInviteLinkCalls++;
    if (inviteLinkFailure != null) return Left(inviteLinkFailure!);
    return Right(fakeInviteLink(
      id: 'inl_new$createInviteLinkCalls',
      eventId: eventId,
      createdAt: DateTime.utc(2026, 11, 22, 12),
    ));
  }

  @override
  Future<Either<Failure, List<InviteLink>>> listInviteLinks({
    required String eventId,
    int page = 0,
    int size = 20,
  }) async =>
      inviteLinkFailure != null ? Left(inviteLinkFailure!) : Right(inviteLinks);

  @override
  Future<Either<Failure, void>> revokeInviteLink({
    required String eventId,
    required String linkId,
  }) async {
    revokedLinkIds.add(linkId);
    return const Right(null);
  }

  @override
  Future<Either<Failure, InviteLinkPreview>> getInviteLinkPreview(
    String token,
  ) async {
    if (previewFailure != null) return Left(previewFailure!);
    final preview = inviteLinkPreview;
    return preview != null
        ? Right(preview)
        : const Left(NotFoundFailure(
            message: 'El enlace no existe o ya no está disponible'));
  }

  @override
  Future<Either<Failure, Invitation>> acceptInviteLink(String token) async {
    acceptedTokens.add(token);
    if (acceptFailure != null) return Left(acceptFailure!);
    return Right(Invitation(
      id: 'inv_1',
      eventId: inviteLinkPreview?.eventId ?? 'evt_1',
      inviterId: 'usr_1',
      inviteeId: 'usr_2',
      status: InvitationStatus.accepted,
      inviteLinkId: 'inl_1',
    ));
  }

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
  Future<Either<Failure, Event>> getEvent(String eventId) async {
    getEventCalls++;
    return eventDetail != null
        ? Right(eventDetail!)
        : const Left(ServerFailure());
  }

  @override
  Future<Either<Failure, List<Event>>> getMyEvents({
    int page = 0,
    int size = 20,
  }) async =>
      myEvents != null ? Right(myEvents!) : const Left(ServerFailure());

  @override
  Future<Either<Failure, List<Event>>> getUpcomingEvents({
    int page = 0,
    int size = 20,
  }) async =>
      upcomingEvents != null
          ? Right(upcomingEvents!)
          : const Left(ServerFailure());

  @override
  Future<Either<Failure, RSVP>> respondToRSVP({
    required String eventId,
    required RSVPStatus status,
    String? message,
  }) async {
    rsvpCalls.add((eventId, status, message));
    if (rsvpResponse != null) return Right(rsvpResponse!);
    if (rsvpFailure != null) return Left(rsvpFailure!);
    return Right(RSVP(
      eventId: eventId,
      userId: 'usr_2',
      status: status,
      message: message,
      respondedAt: DateTime.utc(2026, 11, 20, 15),
    ));
  }

  @override
  Future<Either<Failure, List<Attendee>>> getEventAttendees({
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
    required RSVPStatus status,
    String? message,
  }) =>
      throw UnimplementedError();

  @override
  Future<List<AttendeeModel>> getEventAttendees({
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
  Future<RSVPModel> checkIn({
    required String eventId,
    required String qrCode,
  }) =>
      throw UnimplementedError();

  @override
  Future<String> generateCheckInQR(String eventId) =>
      throw UnimplementedError();

  @override
  Future<List<EventModel>> getInvitedEvents({int page = 0, int size = 20}) =>
      throw UnimplementedError();

  @override
  Future<InviteLinkModel> createInviteLink({
    required String eventId,
    int? expiresInDays,
    int? maxUses,
  }) =>
      throw UnimplementedError();

  @override
  Future<List<InviteLinkModel>> listInviteLinks({
    required String eventId,
    int page = 0,
    int size = 20,
  }) =>
      throw UnimplementedError();

  @override
  Future<void> revokeInviteLink({
    required String eventId,
    required String linkId,
  }) =>
      throw UnimplementedError();

  @override
  Future<InviteLinkPreviewModel> getInviteLinkPreview(String token) =>
      throw UnimplementedError();

  @override
  Future<InvitationModel> acceptInviteLink(String token) =>
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
