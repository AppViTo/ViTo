import 'package:dio/dio.dart';

import '../../../../core/error/exceptions.dart';
import '../models/auth_response_model.dart';
import '../models/user_model.dart';

abstract class AuthRemoteDataSource {
  Future<AuthResponseModel> login({
    required String email,
    required String password,
  });

  Future<AuthResponseModel> register({
    required String username,
    required String email,
    required String password,
  });

  Future<void> logout();

  Future<AuthResponseModel> refreshToken({
    required String refreshToken,
  });

  Future<UserModel> getCurrentUser();
}

class AuthRemoteDataSourceImpl implements AuthRemoteDataSource {
  final Dio dio;

  AuthRemoteDataSourceImpl({required this.dio});

  @override
  Future<AuthResponseModel> login({
    required String email,
    required String password,
  }) async {
    try {
      final response = await dio.post(
        '/auth/login',
        data: {
          'email': email,
          'password': password,
        },
      );

      return AuthResponseModel.fromJson(_asMap(response.data));
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  @override
  Future<AuthResponseModel> register({
    required String username,
    required String email,
    required String password,
  }) async {
    try {
      final response = await dio.post(
        '/auth/register',
        data: {
          'username': username,
          'email': email,
          'password': password,
        },
      );

      return AuthResponseModel.fromJson(_asMap(response.data));
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  @override
  Future<void> logout() async {
    try {
      await dio.post('/auth/logout');
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  @override
  Future<AuthResponseModel> refreshToken({
    required String refreshToken,
  }) async {
    try {
      final response = await dio.post(
        '/auth/refresh',
        data: {
          'refreshToken': refreshToken,
        },
      );

      return AuthResponseModel.fromJson(_asMap(response.data));
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  @override
  Future<UserModel> getCurrentUser() async {
    try {
      final response = await dio.get('/auth/me');

      return UserModel.fromJson(_asMap(response.data));
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  Map<String, dynamic> _asMap(dynamic data) {
    if (data is Map<String, dynamic>) {
      return data;
    }
    throw const ServerException(message: 'Respuesta inesperada del backend');
  }

  Exception _handleError(DioException e) {
    if (e.response != null) {
      final statusCode = e.response!.statusCode;
      final message = _messageOf(e.response!.data);

      if (statusCode == 401) {
        return AuthenticationException(message: message);
      } else if (statusCode == 400) {
        return ValidationException(message: message);
      } else {
        return ServerException(message: message, statusCode: statusCode);
      }
    }

    if (e.type == DioExceptionType.connectionTimeout ||
        e.type == DioExceptionType.receiveTimeout) {
      return const NetworkException(
        message: 'Connection timeout. Please check your internet connection.',
      );
    }

    return const NetworkException(message: 'Network error. Please try again.');
  }

  String _messageOf(dynamic data) {
    if (data is Map) {
      final message = data['message'];
      if (message is String && message.isNotEmpty) {
        return message;
      }
    }
    return 'Unknown error';
  }
}
