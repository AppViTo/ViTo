import 'package:dio/dio.dart';

import 'exceptions.dart';

Exception handleDioError(DioException e) {
  final response = e.response;

  if (response == null) {
    return const NetworkException(message: 'Network error. Please try again.');
  }

  final statusCode = response.statusCode;
  final data = response.data;
  final message = data is Map && data['message'] != null
      ? data['message'].toString()
      : 'Unknown error';

  if (statusCode == 401) {
    return AuthenticationException(message: message);
  }
  if (statusCode == 400) {
    return ValidationException(message: message);
  }

  return ServerException(message: message, statusCode: statusCode);
}
