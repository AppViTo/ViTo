import 'package:dio/dio.dart';

import '../../../../core/error/exceptions.dart';
import '../models/streaming_model.dart';

abstract class StreamingRemoteDataSource {
  Future<LiveStreamModel> createStream({
    required String eventId,
    required String title,
    String? description,
    Map<String, dynamic>? settings,
  });
  
  Future<LiveStreamModel> startStream(String streamId);
  
  Future<LiveStreamModel> stopStream(String streamId);
  
  Future<LiveStreamModel> getStream(String streamId);
  
  Future<LiveStreamModel> getStreamByEventId(String eventId);
  
  Future<List<LiveStreamModel>> getLiveStreams({int page = 0, int size = 20});
  
  Future<ReactionModel> addReaction({
    required String streamId,
    required String type,
    String? emoji,
  });
  
  Future<List<ReactionModel>> getReactions({
    required String streamId,
    int page = 0,
    int size = 50,
  });
  
  Future<ChatMessageModel> sendChatMessage({
    required String streamId,
    required String message,
  });
  
  Future<List<ChatMessageModel>> getChatMessages({
    required String streamId,
    int page = 0,
    int size = 50,
  });
  
  Future<StreamInfoModel> getStreamInfo(String streamId);
}

class StreamingRemoteDataSourceImpl implements StreamingRemoteDataSource {
  final Dio dio;
  
  StreamingRemoteDataSourceImpl({required this.dio});
  
  @override
  Future<LiveStreamModel> createStream({
    required String eventId,
    required String title,
    String? description,
    Map<String, dynamic>? settings,
  }) async {
    try {
      final response = await dio.post(
        '/streaming',
        data: {
          'eventId': eventId,
          'title': title,
          'description': description,
          'settings': settings,
        },
      );
      
      return LiveStreamModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<LiveStreamModel> startStream(String streamId) async {
    try {
      final response = await dio.post('/streaming/$streamId/start');
      return LiveStreamModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<LiveStreamModel> stopStream(String streamId) async {
    try {
      final response = await dio.post('/streaming/$streamId/stop');
      return LiveStreamModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<LiveStreamModel> getStream(String streamId) async {
    try {
      final response = await dio.get('/streaming/$streamId');
      return LiveStreamModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<LiveStreamModel> getStreamByEventId(String eventId) async {
    try {
      final response = await dio.get('/streaming/event/$eventId');
      return LiveStreamModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<List<LiveStreamModel>> getLiveStreams({int page = 0, int size = 20}) async {
    try {
      final response = await dio.get(
        '/streaming/live',
        queryParameters: {'page': page, 'size': size},
      );
      
      return (response.data as List)
          .map((json) => LiveStreamModel.fromJson(json))
          .toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<ReactionModel> addReaction({
    required String streamId,
    required String type,
    String? emoji,
  }) async {
    try {
      final response = await dio.post(
        '/streaming/$streamId/reaction',
        data: {
          'type': type,
          'emoji': emoji,
        },
      );
      
      return ReactionModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<List<ReactionModel>> getReactions({
    required String streamId,
    int page = 0,
    int size = 50,
  }) async {
    try {
      final response = await dio.get(
        '/streaming/$streamId/reactions',
        queryParameters: {'page': page, 'size': size},
      );
      
      return (response.data as List)
          .map((json) => ReactionModel.fromJson(json))
          .toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<ChatMessageModel> sendChatMessage({
    required String streamId,
    required String message,
  }) async {
    try {
      final response = await dio.post(
        '/streaming/$streamId/chat',
        data: {'message': message},
      );
      
      return ChatMessageModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<List<ChatMessageModel>> getChatMessages({
    required String streamId,
    int page = 0,
    int size = 50,
  }) async {
    try {
      final response = await dio.get(
        '/streaming/$streamId/chat',
        queryParameters: {'page': page, 'size': size},
      );
      
      return (response.data as List)
          .map((json) => ChatMessageModel.fromJson(json))
          .toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<StreamInfoModel> getStreamInfo(String streamId) async {
    try {
      final response = await dio.get('/streaming/$streamId/info');
      return StreamInfoModel.fromJson(response.data);
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
    
    throw const NetworkException(message: 'Network error. Please try again.');
  }
}
