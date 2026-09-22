import 'package:dio/dio.dart';

import '../../../../core/error/exceptions.dart';
import '../models/checkin_ticket_model.dart';
import '../models/checkin_event_model.dart';

abstract class CheckInRemoteDataSource {
  Future<CheckInTicketModel> generateTicket(String eventId);
  
  Future<CheckInResultModel> validateAndCheckIn({
    required String qrCode,
    required String eventId,
    String? location,
    double? latitude,
    double? longitude,
  });
  
  Future<CheckInTicketModel> getTicket(String ticketId);
  
  Future<List<CheckInTicketModel>> getEventTickets({
    required String eventId,
    int page = 0,
    int size = 20,
  });
  
  Future<List<CheckInEventModel>> getRecentCheckIns({
    required String eventId,
    int limit = 10,
  });
  
  Future<CheckInStatsModel> getEventStats(String eventId);
  
  Future<void> revokeTicket(String ticketId);
}

class CheckInRemoteDataSourceImpl implements CheckInRemoteDataSource {
  final Dio dio;
  
  CheckInRemoteDataSourceImpl({required this.dio});
  
  @override
  Future<CheckInTicketModel> generateTicket(String eventId) async {
    try {
      final response = await dio.post(
        '/checkin/ticket',
        queryParameters: {'eventId': eventId},
      );
      
      return CheckInTicketModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<CheckInResultModel> validateAndCheckIn({
    required String qrCode,
    required String eventId,
    String? location,
    double? latitude,
    double? longitude,
  }) async {
    try {
      final response = await dio.post(
        '/checkin/validate',
        data: {
          'qrCode': qrCode,
          'eventId': eventId,
          'location': location,
          'latitude': latitude,
          'longitude': longitude,
        },
      );
      
      return CheckInResultModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<CheckInTicketModel> getTicket(String ticketId) async {
    try {
      final response = await dio.get('/checkin/ticket/$ticketId');
      return CheckInTicketModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<List<CheckInTicketModel>> getEventTickets({
    required String eventId,
    int page = 0,
    int size = 20,
  }) async {
    try {
      final response = await dio.get(
        '/checkin/event/$eventId/tickets',
        queryParameters: {'page': page, 'size': size},
      );
      
      return (response.data as List)
          .map((json) => CheckInTicketModel.fromJson(json))
          .toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<List<CheckInEventModel>> getRecentCheckIns({
    required String eventId,
    int limit = 10,
  }) async {
    try {
      final response = await dio.get(
        '/checkin/event/$eventId/recent',
        queryParameters: {'limit': limit},
      );
      
      return (response.data as List)
          .map((json) => CheckInEventModel.fromJson(json))
          .toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<CheckInStatsModel> getEventStats(String eventId) async {
    try {
      final response = await dio.get('/checkin/event/$eventId/stats');
      return CheckInStatsModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<void> revokeTicket(String ticketId) async {
    try {
      await dio.delete('/checkin/ticket/$ticketId');
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
