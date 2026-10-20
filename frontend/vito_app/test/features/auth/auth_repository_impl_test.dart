import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/core/error/exceptions.dart';
import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/features/auth/data/models/auth_response_model.dart';
import 'package:vito_app/features/auth/data/repositories/auth_repository_impl.dart';

import '../../contract/fixture_loader.dart';
import 'fakes.dart';

void main() {
  late FakeAuthRemoteDataSource remote;
  late FakeAuthLocalDataSource local;
  late AuthRepositoryImpl repository;

  AuthResponseModel sessionFrom(String fixturePath) =>
      AuthResponseModel.fromJson(loadFixtureObject(fixturePath));

  setUp(() {
    remote = FakeAuthRemoteDataSource();
    local = FakeAuthLocalDataSource();
    repository = AuthRepositoryImpl(
      remoteDataSource: remote,
      localDataSource: local,
    );
  });

  group('login', () {
    test('persiste el par de tokens del AuthResponse y devuelve el user',
        () async {
      remote.loginResult = sessionFrom('authentication/loginUser.200.json');

      final result = await repository.login(
        email: 'alex@vito.com',
        password: 'vito-dev-2026',
      );

      final user = result.getOrElse(() => throw StateError('Right esperado'));
      expect(user.id, 'usr_01H8X2K4QJ');
      expect(local.accessToken, isNotEmpty);
      expect(local.refreshToken, isNotEmpty);
      expect(local.userId, 'usr_01H8X2K4QJ');
      expect(
        local.ops,
        containsAllInOrder(<String>['saveTokens', 'saveUserId']),
      );
    });

    test('401 del backend → AuthenticationFailure con su mensaje', () async {
      remote.loginError = const AuthenticationException(
        message: 'Credenciales inválidas',
      );

      final result = await repository.login(
        email: 'alex@vito.com',
        password: 'mal-password',
      );

      final failure = result.fold<Failure?>((l) => l, (_) => null);
      expect(failure, isA<AuthenticationFailure>());
      expect(failure!.message, 'Credenciales inválidas');
      expect(local.accessToken, isNull, reason: 'sin sesión que persistir');
    });

    test('400 del backend → ValidationFailure con su mensaje', () async {
      remote.loginError = const ValidationException(
        message: 'El campo password es obligatorio',
      );

      final result = await repository.login(
        email: 'alex@vito.com',
        password: '',
      );

      final failure = result.fold<Failure?>((l) => l, (_) => null);
      expect(failure, isA<ValidationFailure>());
      expect(failure!.message, 'El campo password es obligatorio');
      expect(local.ops, isNot(contains('saveTokens')));
    });
  });

  group('register', () {
    test('persiste la sesión igual que el login', () async {
      remote.registerResult =
          sessionFrom('authentication/registerUser.201.json');

      final result = await repository.register(
        username: 'alex_villa',
        email: 'alex@vito.com',
        password: 'vito-dev-2026',
      );

      expect(result.isRight(), isTrue);
      expect(local.accessToken, isNotEmpty);
      expect(local.refreshToken, isNotEmpty);
    });
  });

  group('refreshToken', () {
    test('rota los tokens guardados con la respuesta del backend', () async {
      local.accessToken = 'access-token-viejo';
      local.refreshToken = 'refresh-token-viejo';
      remote.refreshResult = sessionFrom('authentication/refreshToken.200.json');

      final result = await repository.refreshToken(
        refreshToken: 'refresh-token-viejo',
      );

      expect(result.isRight(), isTrue);
      expect(remote.refreshTokens, ['refresh-token-viejo']);
      expect(local.accessToken, isNot('access-token-viejo'));
      expect(local.refreshToken, isNotEmpty);
    });
  });

  group('restoreSession (auto-login)', () {
    test('sin refresh token guardado no llama al backend', () async {
      final result = await repository.restoreSession();

      expect(result.getOrElse(() => null), isNull);
      expect(remote.calls, isEmpty);
      expect(local.ops, isNot(contains('clearTokens')));
    });

    test('con refresh token guardado devuelve el user y renueva tokens',
        () async {
      local.refreshToken = 'refresh-token-viejo';
      remote.refreshResult = sessionFrom('authentication/refreshToken.200.json');

      final result = await repository.restoreSession();

      final user = result.getOrElse(() => throw StateError('Right esperado'));
      expect(user!.id, 'usr_01H8X2K4QJ');
      expect(remote.refreshTokens, ['refresh-token-viejo']);
      expect(local.accessToken, isNotEmpty);
    });

    test('401 en el refresh: limpia la sesión y devuelve Right(null)', () async {
      local.accessToken = 'access-token-vencido';
      local.refreshToken = 'refresh-token-vencido';
      local.userId = 'usr_01H8X2K4QJ';
      remote.refreshError = const AuthenticationException(
        message: 'El refresh token es inválido o ha expirado',
      );

      final result = await repository.restoreSession();

      expect(result.getOrElse(() => throw StateError('no null')), isNull);
      expect(local.ops, contains('clearTokens'));
      expect(local.accessToken, isNull);
      expect(local.refreshToken, isNull);
      expect(local.userId, isNull);
    });

    test('sin conexión: devuelve Left(NetworkFailure) sin borrar tokens',
        () async {
      local.refreshToken = 'refresh-token-viejo';
      remote.refreshError = const NetworkException(
        message: 'Network error. Please try again.',
      );

      final result = await repository.restoreSession();

      final failure = result.fold<Failure?>((l) => l, (_) => null);
      expect(failure, isA<NetworkFailure>());
      expect(local.refreshToken, 'refresh-token-viejo');
    });
  });

  group('logout', () {
    test('revoca en el backend y limpia el storage local', () async {
      local.accessToken = kAccessToken;
      local.refreshToken = kRefreshToken;

      final result = await repository.logout();

      expect(result.isRight(), isTrue);
      expect(remote.calls, contains('logout'));
      expect(local.accessToken, isNull);
      expect(local.refreshToken, isNull);
    });

    test('si el backend falla igual se limpia la sesión local', () async {
      local.accessToken = kAccessToken;
      local.refreshToken = kRefreshToken;
      remote.logoutError = const ServerException(
        message: 'Server error occurred',
        statusCode: 500,
      );

      final result = await repository.logout();

      expect(result.isLeft(), isTrue);
      expect(local.accessToken, isNull);
      expect(local.refreshToken, isNull);
    });

    test('401 en logout → AuthenticationFailure (token ya inválido)',
        () async {
      remote.logoutError = const AuthenticationException(
        message: 'El token de acceso es inválido o ha expirado',
      );

      final result = await repository.logout();

      final failure = result.fold<Failure?>((l) => l, (_) => null);
      expect(failure, isA<AuthenticationFailure>());
      expect(local.accessToken, isNull);
    });
  });

  group('lectura del storage', () {
    test('si el storage lanza, se devuelve CacheFailure y no crashea',
        () async {
      local.throwOnRead = true;

      final result = await repository.restoreSession();

      final failure = result.fold<Failure?>((l) => l, (_) => null);
      expect(failure, isA<CacheFailure>());
    });
  });
}
