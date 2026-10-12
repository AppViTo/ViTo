import 'package:dio/dio.dart';

import '../../../../core/error/exceptions.dart';
import '../models/archive_model.dart';

abstract class ArchiveRemoteDataSource {
  Future<EventArchiveModel> createArchive({
    required String eventId,
    required String title,
    String? description,
  });
  
  Future<EventArchiveModel> getArchive(String archiveId);
  
  Future<EventArchiveModel> getArchiveByEventId(String eventId);
  
  Future<List<EventArchiveModel>> getPublicArchives({int page = 0, int size = 20});
  
  Future<List<EventArchiveModel>> getUserArchives({int page = 0, int size = 20});
  
  Future<EventArchiveModel> updateArchive({
    required String archiveId,
    String? title,
    String? description,
    String? coverUrl,
    bool? isPublic,
    bool? allowComments,
    bool? allowReactions,
    bool? allowSharing,
  });
  
  Future<ArchiveMediaModel> uploadMedia({
    required String archiveId,
    required String type,
    required String url,
    String? thumbnailUrl,
    String? caption,
    Map<String, dynamic>? metadata,
  });
  
  Future<List<ArchiveMediaModel>> getMedia({
    required String archiveId,
    int page = 0,
    int size = 50,
  });
  
  Future<ArchiveMediaModel> getMediaById(String mediaId);
  
  Future<ArchiveCommentModel> addComment({
    required String archiveId,
    required String content,
    String? mediaId,
    String? parentId,
  });
  
  Future<List<ArchiveCommentModel>> getComments({
    required String archiveId,
    String? mediaId,
    int page = 0,
    int size = 50,
  });
  
  Future<void> updateComment({
    required String commentId,
    required String content,
  });
  
  Future<void> deleteComment(String commentId);
  
  Future<void> likeMedia(String mediaId);
  
  Future<void> unlikeMedia(String mediaId);
}

class ArchiveRemoteDataSourceImpl implements ArchiveRemoteDataSource {
  final Dio dio;
  
  ArchiveRemoteDataSourceImpl({required this.dio});
  
  @override
  Future<EventArchiveModel> createArchive({
    required String eventId,
    required String title,
    String? description,
  }) async {
    try {
      final response = await dio.post(
        '/archive',
        data: {
          'eventId': eventId,
          'title': title,
          'description': description,
        },
      );
      
      return EventArchiveModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<EventArchiveModel> getArchive(String archiveId) async {
    try {
      final response = await dio.get('/archive/$archiveId');
      return EventArchiveModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<EventArchiveModel> getArchiveByEventId(String eventId) async {
    try {
      final response = await dio.get('/archive/event/$eventId');
      return EventArchiveModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<List<EventArchiveModel>> getPublicArchives({int page = 0, int size = 20}) async {
    try {
      final response = await dio.get(
        '/archive/public',
        queryParameters: {'page': page, 'size': size},
      );
      
      return (response.data as List)
          .map((json) => EventArchiveModel.fromJson(json))
          .toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<List<EventArchiveModel>> getUserArchives({int page = 0, int size = 20}) async {
    try {
      final response = await dio.get(
        '/archive/my',
        queryParameters: {'page': page, 'size': size},
      );
      
      return (response.data as List)
          .map((json) => EventArchiveModel.fromJson(json))
          .toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<EventArchiveModel> updateArchive({
    required String archiveId,
    String? title,
    String? description,
    String? coverUrl,
    bool? isPublic,
    bool? allowComments,
    bool? allowReactions,
    bool? allowSharing,
  }) async {
    try {
      final data = <String, dynamic>{};
      if (title != null) data['title'] = title;
      if (description != null) data['description'] = description;
      if (coverUrl != null) data['coverUrl'] = coverUrl;
      if (isPublic != null) data['isPublic'] = isPublic;
      if (allowComments != null) data['allowComments'] = allowComments;
      if (allowReactions != null) data['allowReactions'] = allowReactions;
      if (allowSharing != null) data['allowSharing'] = allowSharing;
      
      final response = await dio.put('/archive/$archiveId', data: data);
      return EventArchiveModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<ArchiveMediaModel> uploadMedia({
    required String archiveId,
    required String type,
    required String url,
    String? thumbnailUrl,
    String? caption,
    Map<String, dynamic>? metadata,
  }) async {
    try {
      final response = await dio.post(
        '/archive/$archiveId/media',
        data: {
          'type': type,
          'url': url,
          'thumbnailUrl': thumbnailUrl,
          'caption': caption,
          'metadata': metadata,
        },
      );
      
      return ArchiveMediaModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<List<ArchiveMediaModel>> getMedia({
    required String archiveId,
    int page = 0,
    int size = 50,
  }) async {
    try {
      final response = await dio.get(
        '/archive/$archiveId/media',
        queryParameters: {'page': page, 'size': size},
      );
      
      return (response.data as List)
          .map((json) => ArchiveMediaModel.fromJson(json))
          .toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<ArchiveMediaModel> getMediaById(String mediaId) async {
    try {
      final response = await dio.get('/archive/media/$mediaId');
      return ArchiveMediaModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<ArchiveCommentModel> addComment({
    required String archiveId,
    required String content,
    String? mediaId,
    String? parentId,
  }) async {
    try {
      final response = await dio.post(
        '/archive/$archiveId/comments',
        data: {
          'content': content,
          'mediaId': mediaId,
          'parentId': parentId,
        },
      );
      
      return ArchiveCommentModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<List<ArchiveCommentModel>> getComments({
    required String archiveId,
    String? mediaId,
    int page = 0,
    int size = 50,
  }) async {
    try {
      final queryParams = <String, dynamic>{
        'page': page,
        'size': size,
      };
      if (mediaId != null) {
        queryParams['mediaId'] = mediaId;
      }
      
      final response = await dio.get(
        '/archive/$archiveId/comments',
        queryParameters: queryParams,
      );
      
      return (response.data as List)
          .map((json) => ArchiveCommentModel.fromJson(json))
          .toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<void> updateComment({
    required String commentId,
    required String content,
  }) async {
    try {
      await dio.put(
        '/archive/comments/$commentId',
        data: {'content': content},
      );
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<void> deleteComment(String commentId) async {
    try {
      await dio.delete('/archive/comments/$commentId');
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<void> likeMedia(String mediaId) async {
    try {
      await dio.post('/archive/media/$mediaId/like');
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<void> unlikeMedia(String mediaId) async {
    try {
      await dio.delete('/archive/media/$mediaId/like');
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
