import 'package:flutter/foundation.dart';

class Env {
  Env._();

  static const String defaultApiBaseUrl = 'http://localhost:8080/api/v1';
  static const String defaultWsBaseUrl = 'ws://localhost:8080';

  static const String _apiBaseUrl = String.fromEnvironment('API_BASE_URL');
  static const String _wsBaseUrl = String.fromEnvironment('WS_BASE_URL');

  static const String environment = String.fromEnvironment(
    'ENVIRONMENT',
    defaultValue: 'development',
  );

  static String get apiBaseUrl =>
      resolveBaseUrl(_apiBaseUrl, defaultApiBaseUrl, name: 'API_BASE_URL');

  static String get wsBaseUrl =>
      resolveBaseUrl(_wsBaseUrl, defaultWsBaseUrl, name: 'WS_BASE_URL');

  /// Correo de soporte que se muestra a quien olvidó su contraseña (Bug-F11).
  /// Vacío si el build no lo define: los correos del equipo no van en el repo.
  static const String supportEmail = String.fromEnvironment('SUPPORT_EMAIL');

  static bool get isProduction => environment == 'production';
  static bool get isDevelopment => environment == 'development';

  static bool get isConfiguredForProduction =>
      _apiBaseUrl.trim().isNotEmpty && _wsBaseUrl.trim().isNotEmpty;

  @visibleForTesting
  static String resolveBaseUrl(
    String configured,
    String fallback, {
    String name = 'API_BASE_URL',
    bool? releaseMode,
  }) {
    final value = configured.trim();
    if (value.isNotEmpty) {
      return value;
    }

    if (releaseMode ?? kReleaseMode) {
      throw StateError(
        '$name no está definida: pasa --dart-define=$name=<url> en el build de producción',
      );
    }

    return fallback;
  }
}
