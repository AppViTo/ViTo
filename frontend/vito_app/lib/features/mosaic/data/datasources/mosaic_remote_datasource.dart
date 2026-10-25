import 'package:dio/dio.dart';

import '../../../../core/error/exceptions.dart';
import '../models/mosaic_model.dart';

abstract class MosaicRemoteDataSource {
  /// `createMosaicPost`: publica una imagen ya subida con `uploadMedia`
  /// (ADR-016). Los check-in los crea `mosaic-service`, no el cliente.
  Future<MosaicFeedItemModel> addPost({
    required String eventId,
    required String mediaId,
    String? content,
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
  
  Future<MosaicLikeStateModel> likePost(String itemId);

  Future<MosaicLikeStateModel> unlikePost(String itemId);

  Future<MosaicFeedItemModel> getFeedItem(String itemId);

  /// `removeMosaicItem` (T-F10, excepción A3): el organizador retira una foto.
  /// `204` sin cuerpo; `403` si no es el organizador y `404` si ya no existe
  /// lanzan [ServerException] con el `message` del envelope (ADR-003).
  Future<void> removeMosaicItem(String itemId);
}

class MosaicRemoteDataSourceImpl implements MosaicRemoteDataSource {
  final Dio dio;
  
  MosaicRemoteDataSourceImpl({required this.dio});
  
  @override
  Future<MosaicFeedItemModel> addPost({
    required String eventId,
    required String mediaId,
    String? content,
  }) async {
    try {
      final response = await dio.post(
        '/mosaic/post',
        data: {
          'eventId': eventId,
          'mediaId': mediaId,
          if (content != null && content.isNotEmpty) 'content': content,
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
  Future<MosaicLikeStateModel> likePost(String itemId) async {
    try {
      final response = await dio.post('/mosaic/item/$itemId/like');
      return MosaicLikeStateModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  @override
  Future<MosaicLikeStateModel> unlikePost(String itemId) async {
    try {
      final response = await dio.delete('/mosaic/item/$itemId/like');
      return MosaicLikeStateModel.fromJson(response.data);
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

  @override
  Future<void> removeMosaicItem(String itemId) async {
    try {
      await dio.delete('/mosaic/item/$itemId');
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
