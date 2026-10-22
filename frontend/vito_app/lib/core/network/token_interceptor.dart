import 'package:dio/dio.dart';

import '../../features/auth/data/datasources/auth_local_datasource.dart';
import 'session_expired_notifier.dart';
import 'token_refresher.dart';

/// Pone `Authorization: Bearer` y, ante un 401, renueva los tokens con
/// `POST /auth/refresh` y reintenta la petición una sola vez (ADR-004).
class TokenInterceptor extends Interceptor {
  final AuthLocalDataSource localDataSource;
  final Dio dio;
  final SessionExpiredNotifier? sessionExpiredNotifier;
  final TokenRefresher tokenRefresher;

  static const skipRefreshKey = TokenRefresher.skipRefreshKey;

  TokenInterceptor({
    required this.localDataSource,
    required this.dio,
    this.sessionExpiredNotifier,
    TokenRefresher? tokenRefresher,
  }) : tokenRefresher = tokenRefresher ??
            TokenRefresher(
              localDataSource: localDataSource,
              dio: dio,
              sessionExpiredNotifier: sessionExpiredNotifier,
            );

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
    final options = err.requestOptions;
    // Un 401 de login/register/refresh/logout es la respuesta en sí
    // (credenciales inválidas, refresh revocado), no un token vencido.
    final isAuthCall = options.path.contains('/auth/');

    if (err.response?.statusCode != 401 ||
        isAuthCall ||
        options.extra[skipRefreshKey] == true) {
      handler.next(err);
      return;
    }

    final newAccessToken = await tokenRefresher.refresh();

    if (newAccessToken == null) {
      handler.next(err);
      return;
    }

    try {
      options.headers['Authorization'] = 'Bearer $newAccessToken';
      options.extra[skipRefreshKey] = true;
      handler.resolve(await dio.fetch(options));
    } on DioException catch (retryError) {
      handler.next(retryError);
    }
  }
}
