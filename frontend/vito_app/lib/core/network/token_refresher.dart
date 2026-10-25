import 'package:dio/dio.dart';

import '../../features/auth/data/datasources/auth_local_datasource.dart';
import '../../features/auth/data/models/auth_response_model.dart';
import 'session_expired_notifier.dart';

/// Renueva el par de tokens con `POST /auth/refresh` (ADR-004).
///
/// Lo comparten el [TokenInterceptor] (401 de REST) y el cliente WebSocket
/// (cierre `4401`). Las llamadas concurrentes comparten un solo refresh: con
/// rotación de refresh token, un segundo intento fallaría.
class TokenRefresher {
  /// Marca las peticiones que no deben disparar otro refresh: el propio
  /// refresh y el reintento tras renovar.
  static const skipRefreshKey = 'skipAuthRefresh';

  final AuthLocalDataSource localDataSource;
  final Dio dio;
  final SessionExpiredNotifier? sessionExpiredNotifier;

  Future<String?>? _refreshing;

  TokenRefresher({
    required this.localDataSource,
    required this.dio,
    this.sessionExpiredNotifier,
  });

  /// Devuelve el nuevo access token, o `null` si no se pudo renovar.
  Future<String?> refresh() {
    return _refreshing ??= _refresh().whenComplete(() {
      _refreshing = null;
    });
  }

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
