import 'package:dio/dio.dart';

import '../../features/auth/data/datasources/auth_local_datasource.dart';

class TokenInterceptor extends Interceptor {
  final AuthLocalDataSource localDataSource;
  final Dio dio;
  
  TokenInterceptor({
    required this.localDataSource,
    required this.dio,
  });
  
  @override
  void onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    final token = await localDataSource.getAccessToken();
    
    if (token != null && token.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    
    handler.next(options);
  }
  
  @override
  void onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    if (err.response?.statusCode == 401) {
      try {
        final refreshToken = await localDataSource.getRefreshToken();
        
        if (refreshToken != null && refreshToken.isNotEmpty) {
          // Attempt to refresh the token
          final response = await dio.post(
            '/auth/refresh',
            data: {'refreshToken': refreshToken},
          );
          
          final newAccessToken = response.data['accessToken'];
          final newRefreshToken = response.data['refreshToken'];
          
          await localDataSource.saveTokens(
            accessToken: newAccessToken,
            refreshToken: newRefreshToken,
          );
          
          // Retry the original request with the new token
          err.requestOptions.headers['Authorization'] = 'Bearer $newAccessToken';
          final retryResponse = await dio.fetch(err.requestOptions);
          
          handler.resolve(retryResponse);
          return;
        }
      } catch (e) {
        // If refresh fails, clear tokens and propagate the error
        await localDataSource.clearTokens();
      }
    }
    
    handler.next(err);
  }
}
