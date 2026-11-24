import 'package:dio/dio.dart';

import '../../../../core/error/dio_error_handler.dart';
import '../../domain/entities/rsvp.dart';
import '../models/attendee_model.dart';
import '../models/event_model.dart';
import '../models/invitation_model.dart';
import '../models/invite_link_model.dart';
import '../models/rsvp_model.dart';

abstract class EventRemoteDataSource {
  Future<EventModel> createEvent({
    required String title,
    String? description,
    Map<String, dynamic>? details,
    Map<String, dynamic>? settings,
  });
  
  /// `updateEvent` (`PATCH /events/{eventId}`, Ola 1): [patch] lleva solo
  /// lo que cambia (`application/merge-patch+json`, ver `eventPatchJson`).
  Future<EventModel> updateEvent({
    required String eventId,
    required Map<String, dynamic> patch,
  });

  Future<void> cancelEvent(String eventId);
  
  Future<EventModel> getEvent(String eventId);
  
  Future<List<EventModel>> getMyEvents({int page = 0, int size = 20});
  
  Future<List<EventModel>> getUpcomingEvents({int page = 0, int size = 20});
  
  /// `respondRsvp` (`POST /events/{eventId}/rsvp`): la única forma de
  /// responder una invitación desde la Ola 1 (D-01).
  Future<RSVPModel> respondToRSVP({
    required String eventId,
    required RSVPStatus status,
    String? message,
  });

  /// `listEventAttendees`: solo el organizador.
  Future<List<AttendeeModel>> getEventAttendees({
    required String eventId,
    int page = 0,
    int size = 20,
  });
  
  Future<InvitationModel> sendInvitation({
    required String eventId,
    required String inviteeId,
    String? message,
  });
  
  Future<List<InvitationModel>> getEventInvitations({
    required String eventId,
    int page = 0,
    int size = 20,
  });
  
  Future<RSVPModel> checkIn({
    required String eventId,
    required String qrCode,
  });

  Future<String> generateCheckInQR(String eventId);

  /// `listEvents?scope=invited` (Ola 1): eventos a los que invitaron a quien
  /// consulta, por fecha de inicio ascendente.
  Future<List<EventModel>> getInvitedEvents({int page = 0, int size = 20});

  /// `createInviteLink`: solo el organizador.
  Future<InviteLinkModel> createInviteLink({
    required String eventId,
    int? expiresInDays,
    int? maxUses,
  });

  /// `listInviteLinks`: solo el organizador.
  Future<List<InviteLinkModel>> listInviteLinks({
    required String eventId,
    int page = 0,
    int size = 20,
  });

  /// `revokeInviteLink`: idempotente.
  Future<void> revokeInviteLink({
    required String eventId,
    required String linkId,
  });

  /// `getInviteLinkPreview`: público, sin sesión.
  Future<InviteLinkPreviewModel> getInviteLinkPreview(String token);

  /// `acceptInviteLink`: idempotente; devuelve la invitación aceptada.
  Future<InvitationModel> acceptInviteLink(String token);
}

class EventRemoteDataSourceImpl implements EventRemoteDataSource {
  final Dio dio;
  
  EventRemoteDataSourceImpl({required this.dio});
  
  @override
  Future<EventModel> createEvent({
    required String title,
    String? description,
    Map<String, dynamic>? details,
    Map<String, dynamic>? settings,
  }) async {
    try {
      final response = await dio.post(
        '/events',
        data: {
          'title': title,
          'description': description,
          'details': details,
          'settings': settings,
        },
      );
      
      return EventModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<EventModel> updateEvent({
    required String eventId,
    required Map<String, dynamic> patch,
  }) async {
    try {
      final response = await dio.patch(
        '/events/$eventId',
        data: patch,
        options: Options(contentType: 'application/merge-patch+json'),
      );

      return EventModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<void> cancelEvent(String eventId) async {
    try {
      await dio.delete('/events/$eventId');
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<EventModel> getEvent(String eventId) async {
    try {
      final response = await dio.get('/events/$eventId');
      return EventModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<List<EventModel>> getMyEvents({int page = 0, int size = 20}) =>
      _listEvents(scope: 'mine', page: page, size: size);

  @override
  Future<List<EventModel>> getUpcomingEvents({int page = 0, int size = 20}) =>
      _listEvents(scope: 'upcoming', page: page, size: size);

  @override
  Future<List<EventModel>> getInvitedEvents({int page = 0, int size = 20}) =>
      _listEvents(scope: 'invited', page: page, size: size);

  @override
  Future<InviteLinkModel> createInviteLink({
    required String eventId,
    int? expiresInDays,
    int? maxUses,
  }) async {
    try {
      final response = await dio.post(
        '/events/$eventId/invite-links',
        data: createInviteLinkRequestJson(
          expiresInDays: expiresInDays,
          maxUses: maxUses,
        ),
      );
      return InviteLinkModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  @override
  Future<List<InviteLinkModel>> listInviteLinks({
    required String eventId,
    int page = 0,
    int size = 20,
  }) async {
    try {
      final response = await dio.get(
        '/events/$eventId/invite-links',
        queryParameters: {'page': page, 'size': size},
      );
      return (response.data as List)
          .map((json) => InviteLinkModel.fromJson(json as Map<String, dynamic>))
          .toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  @override
  Future<void> revokeInviteLink({
    required String eventId,
    required String linkId,
  }) async {
    try {
      await dio.delete('/events/$eventId/invite-links/$linkId');
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  @override
  Future<InviteLinkPreviewModel> getInviteLinkPreview(String token) async {
    try {
      final response = await dio.get('/invite-links/$token');
      return InviteLinkPreviewModel.fromJson(
          response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  @override
  Future<InvitationModel> acceptInviteLink(String token) async {
    try {
      final response = await dio.post('/invite-links/$token/accept');
      return InvitationModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  /// `listEvents` del contrato: `GET /events?scope=mine|upcoming` (ADR-014).
  /// La respuesta es un array plano de `Event` (ADR-002).
  Future<List<EventModel>> _listEvents({
    required String scope,
    required int page,
    required int size,
  }) async {
    try {
      final response = await dio.get(
        '/events',
        queryParameters: {'scope': scope, 'page': page, 'size': size},
      );

      return (response.data as List)
          .map((json) => EventModel.fromJson(json as Map<String, dynamic>))
          .toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<RSVPModel> respondToRSVP({
    required String eventId,
    required RSVPStatus status,
    String? message,
  }) async {
    try {
      final response = await dio.post(
        '/events/$eventId/rsvp',
        data: respondRsvpRequestJson(status: status, message: message),
      );
      
      return RSVPModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<List<AttendeeModel>> getEventAttendees({
    required String eventId,
    int page = 0,
    int size = 20,
  }) async {
    try {
      final response = await dio.get(
        '/events/$eventId/attendees',
        queryParameters: {'page': page, 'size': size},
      );

      return (response.data as List)
          .map((json) => AttendeeModel.fromJson(json as Map<String, dynamic>))
          .toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<InvitationModel> sendInvitation({
    required String eventId,
    required String inviteeId,
    String? message,
  }) async {
    try {
      final response = await dio.post(
        '/events/$eventId/invitations',
        data: {
          'inviteeId': inviteeId,
          'message': message,
        },
      );
      
      return InvitationModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<List<InvitationModel>> getEventInvitations({
    required String eventId,
    int page = 0,
    int size = 20,
  }) async {
    try {
      final response = await dio.get(
        '/events/$eventId/invitations',
        queryParameters: {'page': page, 'size': size},
      );
      
      return (response.data as List)
          .map((json) => InvitationModel.fromJson(json))
          .toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<RSVPModel> checkIn({
    required String eventId,
    required String qrCode,
  }) async {
    try {
      final response = await dio.post(
        '/events/$eventId/check-in',
        data: {'qrCode': qrCode},
      );
      
      return RSVPModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<String> generateCheckInQR(String eventId) async {
    try {
      final response = await dio.get('/events/$eventId/qr-code');
      return response.data;
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  Exception _handleError(DioException e) => handleDioError(e);
}
