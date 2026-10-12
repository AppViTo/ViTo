import 'package:dio/dio.dart';

import '../../../../core/error/exceptions.dart';
import '../models/mosaic_model.dart';

abstract class MosaicRemoteDataSource {
  Future<MosaicFeedItemModel> addPost({
    required String eventId,
    required String content,
  });
  
  Future<MosaicFeedItemModel> addPhoto({
    required String eventId,
    String? content,
    required List<String> mediaUrls,
  });
  
  Future<MosaicFeedItemModel> addCheckIn({
    required String eventId,
    String? location,
  });
  
  Future<List<MosaicFeedItemModel>> getEventFeed({
    required String eventId,
    int page = 0,
    int size = 20,
  });
  
  Future<MosaicGridModel> getEventGrid(String eventId);
  
  Future<MosaicGridModel> initializeGrid({
    required String eventId,
    int rows = 4,
    int cols = 4,
  });
  
  Future<void> likePost(String itemId);
  
  Future<void> unlikePost(String itemId);
  
  Future<MosaicFeedItemModel> getFeedItem(String itemId);
}

class MosaicRemoteDataSourceImpl implements MosaicRemoteDataSource {
  final Dio dio;
  
  MosaicRemoteDataSourceImpl({required this.dio});
  
  @override
  Future<MosaicFeedItemModel> addPost({
    required String eventId,
    required String content,
  }) async {
    try {
      final response = await dio.post(
        '/mosaic/post',
        data: {
          'eventId': eventId,
          'content': content,
        },
      );
      
      return MosaicFeedItemModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<MosaicFeedItemModel> addPhoto({
    required String eventId,
    String? content,
    required List<String> mediaUrls,
  }) async {
    try {
      final response = await dio.post(
        '/mosaic/photo',
        data: {
          'eventId': eventId,
          'content': content,
          'mediaUrls': mediaUrls,
        },
      );
      
      return MosaicFeedItemModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<MosaicFeedItemModel> addCheckIn({
    required String eventId,
    String? location,
  }) async {
    try {
      final response = await dio.post(
        '/mosaic/checkin',
        data: {
          'eventId': eventId,
          'location': location,
        },
      );
      
      return MosaicFeedItemModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<List<MosaicFeedItemModel>> getEventFeed({
    required String eventId,
    int page = 0,
    int size = 20,
  }) async {
    try {
      final response = await dio.get(
        '/mosaic/feed/$eventId',
        queryParameters: {'page': page, 'size': size},
      );
      
      return (response.data as List)
          .map((json) => MosaicFeedItemModel.fromJson(json))
          .toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<MosaicGridModel> getEventGrid(String eventId) async {
    try {
      final response = await dio.get('/mosaic/grid/$eventId');
      return MosaicGridModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<MosaicGridModel> initializeGrid({
    required String eventId,
    int rows = 4,
    int cols = 4,
  }) async {
    try {
      final response = await dio.post(
        '/mosaic/grid/$eventId/initialize',
        queryParameters: {'rows': rows, 'cols': cols},
      );
      
      return MosaicGridModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<void> likePost(String itemId) async {
    try {
      await dio.post('/mosaic/item/$itemId/like');
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<void> unlikePost(String itemId) async {
    try {
      await dio.delete('/mosaic/item/$itemId/like');
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<MosaicFeedItemModel> getFeedItem(String itemId) async {
    try {
      final response = await dio.get('/mosaic/item/$itemId');
      return MosaicFeedItemModel.fromJson(response.data);
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
