import 'package:dio/dio.dart';

import '../../../../core/error/exceptions.dart';
import '../models/event_model.dart';
import '../models/rsvp_model.dart';
import '../models/invitation_model.dart';

abstract class EventRemoteDataSource {
  Future<EventModel> createEvent({
    required String title,
    String? description,
    Map<String, dynamic>? details,
    Map<String, dynamic>? settings,
  });
  
  Future<EventModel> updateEvent({
    required String eventId,
    String? title,
    String? description,
    Map<String, dynamic>? details,
    Map<String, dynamic>? settings,
  });
  
  Future<void> cancelEvent(String eventId);
  
  Future<EventModel> getEvent(String eventId);
  
  Future<List<EventModel>> getMyEvents({int page = 0, int size = 20});
  
  Future<List<EventModel>> getUpcomingEvents({int page = 0, int size = 20});
  
  Future<RSVPModel> respondToRSVP({
    required String eventId,
    required String status,
    String? message,
    int guestsCount = 0,
  });
  
  Future<List<RSVPModel>> getEventAttendees({
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
  
  Future<void> respondToInvitation({
    required String eventId,
    required String status,
  });
  
  Future<RSVPModel> checkIn({
    required String eventId,
    required String qrCode,
  });
  
  Future<String> generateCheckInQR(String eventId);
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
    String? title,
    String? description,
    Map<String, dynamic>? details,
    Map<String, dynamic>? settings,
  }) async {
    try {
      final response = await dio.put(
        '/events/$eventId',
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
  Future<List<EventModel>> getMyEvents({int page = 0, int size = 20}) async {
    try {
      final response = await dio.get(
        '/events/my-events',
        queryParameters: {'page': page, 'size': size},
      );
      
      return (response.data as List)
          .map((json) => EventModel.fromJson(json))
          .toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<List<EventModel>> getUpcomingEvents({int page = 0, int size = 20}) async {
    try {
      final response = await dio.get(
        '/events/upcoming',
        queryParameters: {'page': page, 'size': size},
      );
      
      return (response.data as List)
          .map((json) => EventModel.fromJson(json))
          .toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<RSVPModel> respondToRSVP({
    required String eventId,
    required String status,
    String? message,
    int guestsCount = 0,
  }) async {
    try {
      final response = await dio.post(
        '/events/$eventId/rsvp',
        data: {
          'status': status,
          'message': message,
          'guestsCount': guestsCount,
        },
      );
      
      return RSVPModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<List<RSVPModel>> getEventAttendees({
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
          .map((json) => RSVPModel.fromJson(json))
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
  Future<void> respondToInvitation({
    required String eventId,
    required String status,
  }) async {
    try {
      await dio.post(
        '/events/$eventId/invitations/respond',
        data: {'status': status},
      );
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
  
  Exception _handleError(DioException e) {
    if (e.response != null) {
      final statusCode = e.response!.statusCode;
      final message = e.response!.data['message'] ?? 'Unknown error';
      
      if (statusCode == 401) {
        throw AuthenticationException(message: message);
      } else if (statusCode == 400) {
        throw ValidationException(message: message);
      } else {
        throw ServerException(message: message, statusCode: statusCode);
      }
    }
    
    throw NetworkException(message: 'Network error. Please try again.');
  }
}
