import 'package:dio/dio.dart';

import '../../../../core/error/exceptions.dart';
import '../models/social_profile_model.dart';

abstract class SocialRemoteDataSource {
  Future<void> follow(String userId);
  
  Future<void> unfollow(String userId);
  
  Future<bool> isFollowing(String userId);
  
  Future<SocialProfileModel> getProfile(String userId);
  
  Future<List<SocialProfileModel>> getFollowers({
    required String userId,
    int page = 0,
    int size = 20,
  });
  
  Future<List<SocialProfileModel>> getFollowing({
    required String userId,
    int page = 0,
    int size = 20,
  });
  
  Future<List<SocialProfileModel>> searchUsers({
    required String query,
    int page = 0,
    int size = 20,
  });
}

class SocialRemoteDataSourceImpl implements SocialRemoteDataSource {
  final Dio dio;
  
  SocialRemoteDataSourceImpl({required this.dio});
  
  @override
  Future<void> follow(String userId) async {
    try {
      await dio.post('/social/follow/$userId');
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<void> unfollow(String userId) async {
    try {
      await dio.delete('/social/follow/$userId');
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<bool> isFollowing(String userId) async {
    try {
      final response = await dio.get('/social/is-following/$userId');
      return response.data;
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<SocialProfileModel> getProfile(String userId) async {
    try {
      final response = await dio.get('/social/profile/$userId');
      return SocialProfileModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<List<SocialProfileModel>> getFollowers({
    required String userId,
    int page = 0,
    int size = 20,
  }) async {
    try {
      final response = await dio.get(
        '/social/followers/$userId',
        queryParameters: {'page': page, 'size': size},
      );
      
      return (response.data as List)
          .map((json) => SocialProfileModel.fromJson(json))
          .toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<List<SocialProfileModel>> getFollowing({
    required String userId,
    int page = 0,
    int size = 20,
  }) async {
    try {
      final response = await dio.get(
        '/social/following/$userId',
        queryParameters: {'page': page, 'size': size},
      );
      
      return (response.data as List)
          .map((json) => SocialProfileModel.fromJson(json))
          .toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }
  
  @override
  Future<List<SocialProfileModel>> searchUsers({
    required String query,
    int page = 0,
    int size = 20,
  }) async {
    try {
      final response = await dio.get(
        '/social/search',
        queryParameters: {'query': query, 'page': page, 'size': size},
      );
      
      return (response.data as List)
          .map((json) => SocialProfileModel.fromJson(json))
          .toList();
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
