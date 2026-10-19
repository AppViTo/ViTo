import 'package:dio/dio.dart';

import '../../features/auth/data/datasources/auth_local_datasource.dart';
import '../../features/auth/data/models/auth_response_model.dart';
import 'session_expired_notifier.dart';

/// Pone `Authorization: Bearer` y, ante un 401, renueva los tokens con
/// `POST /auth/refresh` y reintenta la petición una sola vez (ADR-004).
class TokenInterceptor extends Interceptor {
  final AuthLocalDataSource localDataSource;
  final Dio dio;
  final SessionExpiredNotifier? sessionExpiredNotifier;

  /// Marca de las peticiones que no deben disparar otro refresh: el propio
  /// refresh y el reintento tras renovar.
  static const skipRefreshKey = 'skipAuthRefresh';

  /// Refresh en curso: varias peticiones con 401 a la vez comparten uno solo
  /// (con rotación de refresh token, un segundo refresh fallaría).
  Future<String?>? _refreshing;

  TokenInterceptor({
    required this.localDataSource,
    required this.dio,
    this.sessionExpiredNotifier,
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

    final newAccessToken = await (_refreshing ??= _refresh().whenComplete(() {
      _refreshing = null;
    }));

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

  /// Devuelve el nuevo access token, o `null` si no se pudo renovar (en ese
  /// caso limpia la sesión y avisa a [sessionExpiredNotifier]).
  Future<String?> _refresh() async {
    final refreshToken = await localDataSource.getRefreshToken();
    if (refreshToken == null || refreshToken.isEmpty) {
      return null;
    }

    try {
      final response = await dio.post(
        '/auth/refresh',
        data: {'refreshToken': refreshToken},
        options: Options(extra: {skipRefreshKey: true}),
      );
      final auth = AuthResponseModel.fromJson(response.data);

      await localDataSource.saveTokens(
        accessToken: auth.accessToken,
        refreshToken: auth.refreshToken,
      );
      return auth.accessToken;
    } catch (_) {
      await localDataSource.clearTokens();
      sessionExpiredNotifier?.notify();
      return null;
    }
  }
}
