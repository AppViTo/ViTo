import 'package:dio/dio.dart';

import '../../features/auth/data/datasources/auth_local_datasource.dart';

class TokenInterceptor extends Interceptor {
  final AuthLocalDataSource localDataSource;
  final Dio dio;

  /// Marca que una petición ya fue reintentada tras un refresh, para no
  /// entrar en bucle si el reintento vuelve a devolver 401.
  static const String _retriedKey = 'tokenRetried';

  /// Rutas públicas (sin `Authorization` en el contrato): un 401 ahí es
  /// credencial/refresh inválido, nunca se reintenta con un refresh.
  static const List<String> _publicAuthPaths = [
    '/auth/login',
    '/auth/register',
    '/auth/refresh',
    '/auth/logout',
  ];

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
    final path = err.requestOptions.path;
    final canRetry = err.response?.statusCode == 401 &&
        !_publicAuthPaths.any((p) => path.startsWith(p)) &&
        err.requestOptions.extra[_retriedKey] != true;

    if (canRetry) {
      try {
        final refreshToken = await localDataSource.getRefreshToken();

        if (refreshToken != null && refreshToken.isNotEmpty) {
          // POST /auth/refresh (público, ADR-004).
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

          // Reintento con el token nuevo (el interceptor de arriba vuelve a
          // leerlo del storage); si vuelve a fallar, no se reintenta otra vez.
          err.requestOptions.extra = {
            ...err.requestOptions.extra,
            _retriedKey: true,
          };
          err.requestOptions.headers['Authorization'] = 'Bearer $newAccessToken';
          final retryResponse = await dio.fetch(err.requestOptions);

          handler.resolve(retryResponse);
          return;
        }
      } catch (_) {
        // Refresh falló: se limpia la sesión y se propaga el 401 original.
        await localDataSource.clearTokens();
      }
    }

    handler.next(err);
  }
}
