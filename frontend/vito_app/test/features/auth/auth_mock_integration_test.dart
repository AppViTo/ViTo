import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/core/error/exceptions.dart';
import 'package:vito_app/features/auth/data/datasources/auth_remote_datasource.dart';
import 'package:vito_app/features/auth/data/models/auth_response_model.dart';

/// Verificación de T-F01 contra el mock del contrato (`npm run mock:api`,
/// Prism en el puerto 4010). Si el mock no está levantado (p.ej. CI) los
/// tests se saltan; nada de esto toca la red real.
const String kMockApiUrl = String.fromEnvironment(
  'MOCK_API_URL',
  defaultValue: 'http://localhost:4010',
);

void main() {
  late Dio dio;
  late AuthRemoteDataSource dataSource;

  setUp(() {
    dio = Dio(BaseOptions(
      baseUrl: '$kMockApiUrl/api/v1',
      connectTimeout: const Duration(seconds: 2),
      receiveTimeout: const Duration(seconds: 5),
    ));
    dataSource = AuthRemoteDataSourceImpl(dio: dio);
  });

  /// `false` si el mock no responde (el 400 del propio mock sí cuenta como
  /// "está arriba": valida el body igual que Prism).
  Future<bool> mockUp() async {
    try {
      await dio.post('/auth/login', data: <String, dynamic>{});
      return true;
    } on DioException catch (e) {
      return e.response != null;
    } catch (_) {
      return false;
    }
  }

  Future<bool> requireMock() async {
    if (await mockUp()) return true;
    markTestSkipped('Mock del contrato no disponible: npm run mock:api');
    return false;
  }

  test('login contra el mock devuelve el AuthResponse completo (ADR-004)',
      () async {
    if (!await requireMock()) return;

    final session = await dataSource.login(
      email: 'alex@vito.com',
      password: 'vito-dev-2026',
    );

    expect(session.accessToken, isNotEmpty);
    expect(session.refreshToken, isNotEmpty);
    expect(session.user.id, isNotEmpty);
    // Con `--dynamic` Prism rellena el schema con datos fake (no los del
    // fixture): lo que garantiza el contrato es que `email` venga como String.
    expect(session.user.email, isA<String>());
    expect(session.user.email, isNotEmpty);
  });

  test('register contra el mock devuelve tokens y user', () async {
    if (!await requireMock()) return;

    final session = await dataSource.register(
      username: 'alex_villa',
      email: 'alex@vito.com',
      password: 'vito-dev-2026',
    );

    expect(session.accessToken, isNotEmpty);
    expect(session.refreshToken, isNotEmpty);
    expect(session.user.username, isNotEmpty);
  });

  test('refresh contra el mock rota el par de tokens', () async {
    if (!await requireMock()) return;

    final session = await dataSource.refreshToken(
      refreshToken: 'refresh-token-viejo',
    );

    expect(session.accessToken, isNotEmpty);
    expect(session.refreshToken, isNotEmpty);
    expect(session.user.id, isNotEmpty);
  });

  test('body inválido contra el mock → ValidationException (400)', () async {
    if (!await requireMock()) return;

    await expectLater(
      dataSource.login(email: '', password: ''),
      throwsA(isA<ValidationException>()),
    );
  });

  test('AuthResponseModel del mock sobrevive fromJson → toJson', () async {
    if (!await requireMock()) return;

    final session = await dataSource.login(
      email: 'alex@vito.com',
      password: 'vito-dev-2026',
    );

    final roundTrip = AuthResponseModel.fromJson(session.toJson());

    expect(roundTrip.accessToken, session.accessToken);
    expect(roundTrip.refreshToken, session.refreshToken);
    expect(roundTrip.user.id, session.user.id);
  });
}
