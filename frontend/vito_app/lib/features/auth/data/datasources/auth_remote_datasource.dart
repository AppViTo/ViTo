import 'package:dio/dio.dart';

import '../../../../core/error/exceptions.dart';
import '../models/auth_response_model.dart';
import '../models/user_model.dart';

abstract class AuthRemoteDataSource {
  Future<AuthResponseModel> login({
    required String email,
    required String password,
  });

  /// `RegisterRequest` del contrato solo declara `username`, `email` y
  /// `password`: no se envía `displayName`.
  Future<AuthResponseModel> register({
    required String username,
    required String email,
    required String password,
  });

  Future<void> logout();

  Future<AuthResponseModel> refreshToken({
    required String refreshToken,
  });

  /// `GET /auth/me` — fuera del contrato hasta la Ola 1; no se usa.
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

      return AuthResponseModel.fromJson(response.data);
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

      return AuthResponseModel.fromJson(response.data);
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

      return AuthResponseModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  @override
  Future<UserModel> getCurrentUser() async {
    try {
      final response = await dio.get('/auth/me');

      return UserModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  /// Traduce el envelope de error de ADR-003 (`{error, message, status, …}`)
  /// a la excepción de cada status. `message` ya viene en es-ES del backend.
  Exception _handleError(DioException e) {
    final response = e.response;
    if (response != null) {
      final data = response.data;
      final message = data is Map && data['message'] is String
          ? data['message'] as String
          : 'Error inesperado del servidor';

      switch (response.statusCode) {
        case 400:
          return ValidationException(message: message);
        case 401:
          return AuthenticationException(message: message);
        case 403:
          return AuthorizationException(message: message);
        case 409:
          return ConflictException(message: message);
        default:
          return ServerException(message: message, statusCode: response.statusCode);
      }
    }

    if (e.type == DioExceptionType.connectionTimeout ||
        e.type == DioExceptionType.receiveTimeout) {
      return const NetworkException(message: 'Connection timeout. Please check your internet connection.');
    }

    return const NetworkException(message: 'Network error. Please try again.');
  }
}
