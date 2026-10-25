import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/core/network/session_expired_notifier.dart';
import 'package:vito_app/core/network/token_interceptor.dart';

import '../../contract/fixture_loader.dart';
import '../../features/auth/fakes.dart';

/// T-F01 — Refresh automático del `TokenInterceptor` (ADR-004).
void main() {
  late ScriptedAdapter adapter;
  late InMemoryAuthLocalDataSource local;
  late SessionExpiredNotifier notifier;
  late Dio dio;
  late int expiredCount;

  final refresh200 = loadFixtureObject('authentication/refreshToken.200.json');
  final events200 = loadFixtureList('events/listEvents.mine.200.json');
  final events401 = loadFixtureObject('events/listEvents.401.json');
  final refresh401 = loadFixtureObject('authentication/refreshToken.401.json');

  setUp(() {
    adapter = ScriptedAdapter();
    local = InMemoryAuthLocalDataSource(
      accessToken: 'vencido',
      refreshToken: 'refresh-guardado',
    );
    notifier = SessionExpiredNotifier();
    expiredCount = 0;
    notifier.stream.listen((_) => expiredCount++);
    dio = Dio(BaseOptions(baseUrl: 'http://localhost:4010/api/v1'))
      ..httpClientAdapter = adapter;
    dio.interceptors.add(TokenInterceptor(
      localDataSource: local,
      dio: dio,
      sessionExpiredNotifier: notifier,
    ));
  });

  tearDown(() => notifier.dispose());

  test('envía el access token guardado como Bearer', () async {
    adapter.on('GET /events', [StubResponse(200, events200)]);

    await dio.get('/events');

    expect(adapter.requests.single.headers['Authorization'], 'Bearer vencido');
  });

  test('401: renueva con el refresh token y reintenta con el token nuevo',
      () async {
    adapter
      ..on('GET /events', [
        StubResponse(401, events401),
        StubResponse(200, events200),
      ])
      ..on('POST /auth/refresh', [StubResponse(200, refresh200)]);

    final response = await dio.get('/events');

    expect(response.statusCode, 200);
    expect(adapter.count('POST /auth/refresh'), 1);
    expect(adapter.requests.last.headers['Authorization'],
        'Bearer ${refresh200['accessToken']}');
    expect(local.accessToken, refresh200['accessToken']);
    expect(local.refreshToken, refresh200['refreshToken']);
    expect(expiredCount, 0);
  });

  test('dos 401 a la vez comparten un solo refresh', () async {
    adapter
      ..on('GET /events', [
        StubResponse(401, events401),
        StubResponse(401, events401),
        StubResponse(200, events200),
      ])
      ..on('POST /auth/refresh', [StubResponse(200, refresh200)]);

    await Future.wait([dio.get('/events'), dio.get('/events')]);

    expect(adapter.count('POST /auth/refresh'), 1);
  });

  test('refresh rechazado: limpia la sesión, avisa y no entra en bucle',
      () async {
    adapter
      ..on('GET /events', [StubResponse(401, events401)])
      ..on('POST /auth/refresh', [StubResponse(401, refresh401)]);

    await expectLater(
      dio.get('/events'),
      throwsA(isA<DioException>()
          .having((e) => e.response?.statusCode, 'status', 401)),
    );

    expect(adapter.count('POST /auth/refresh'), 1);
    expect(local.accessToken, isNull);
    expect(local.refreshToken, isNull);
    await Future<void>.delayed(Duration.zero);
    expect(expiredCount, 1);
  });

  test('si el reintento vuelve a dar 401, no renueva otra vez', () async {
    adapter
      ..on('GET /events', [StubResponse(401, events401)])
      ..on('POST /auth/refresh', [StubResponse(200, refresh200)]);

    await expectLater(dio.get('/events'), throwsA(isA<DioException>()));

    expect(adapter.count('POST /auth/refresh'), 1);
    expect(adapter.count('GET /events'), 2);
  });

  test('un 401 de /auth/* no dispara refresh', () async {
    adapter.on('POST /auth/login', [
      StubResponse(401, loadFixtureObject('authentication/loginUser.401.json')),
    ]);

    await expectLater(dio.post('/auth/login'), throwsA(isA<DioException>()));

    expect(adapter.count('POST /auth/refresh'), 0);
    expect(local.refreshToken, 'refresh-guardado');
  });
}
