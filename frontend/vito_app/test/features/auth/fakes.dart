import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';

import 'package:vito_app/features/auth/data/datasources/auth_local_datasource.dart';

/// Almacén de tokens en memoria (sustituye a FlutterSecureStorage).
class InMemoryAuthLocalDataSource implements AuthLocalDataSource {
  String? accessToken;
  String? refreshToken;
  String? userId;
  int clearCount = 0;

  InMemoryAuthLocalDataSource({this.accessToken, this.refreshToken});

  @override
  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
  }

  @override
  Future<String?> getAccessToken() async => accessToken;

  @override
  Future<String?> getRefreshToken() async => refreshToken;

  @override
  Future<void> clearTokens() async {
    clearCount++;
    accessToken = null;
    refreshToken = null;
    userId = null;
  }

  @override
  Future<bool> isLoggedIn() async => accessToken?.isNotEmpty ?? false;

  @override
  Future<void> saveUserId(String userId) async => this.userId = userId;

  @override
  Future<String?> getUserId() async => userId;
}

class StubResponse {
  final int status;
  final Object? body;

  const StubResponse(this.status, [this.body]);
}

/// Adaptador HTTP que responde según `"METHOD /path"` y registra cada petición.
/// Varias respuestas para la misma ruta se consumen en orden; la última se repite.
class ScriptedAdapter implements HttpClientAdapter {
  final Map<String, List<StubResponse>> _routes = {};
  final List<RequestOptions> requests = [];
  final List<String> bodies = [];

  void on(String route, List<StubResponse> responses) {
    _routes[route] = List.of(responses);
  }

  int count(String route) =>
      requests.where((r) => '${r.method} ${r.path}' == route).length;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    final buffer = <int>[];
    if (requestStream != null) {
      await for (final chunk in requestStream) {
        buffer.addAll(chunk);
      }
    }
    bodies.add(utf8.decode(buffer, allowMalformed: true));

    final route = '${options.method} ${options.path}';
    final queue = _routes[route];
    if (queue == null || queue.isEmpty) {
      throw StateError('Sin respuesta preparada para $route');
    }
    final stub = queue.length > 1 ? queue.removeAt(0) : queue.first;

    return ResponseBody.fromString(
      stub.body == null ? '' : jsonEncode(stub.body),
      stub.status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
