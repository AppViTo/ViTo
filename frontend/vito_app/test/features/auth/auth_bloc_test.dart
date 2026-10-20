import 'dart:convert';

import 'package:bloc_test/bloc_test.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/core/network/session_expired_notifier.dart';
import 'package:vito_app/core/network/token_interceptor.dart';
import 'package:vito_app/features/auth/data/datasources/auth_remote_datasource.dart';
import 'package:vito_app/features/auth/data/repositories/auth_repository_impl.dart';
import 'package:vito_app/features/auth/domain/usecases/login.dart';
import 'package:vito_app/features/auth/domain/usecases/logout.dart';
import 'package:vito_app/features/auth/domain/usecases/register.dart';
import 'package:vito_app/features/auth/domain/usecases/restore_session.dart';
import 'package:vito_app/features/auth/presentation/bloc/auth_bloc.dart';

import '../../contract/fixture_loader.dart';
import 'fakes.dart';

/// T-F01 — AuthBloc contra el stack real (datasource → repositorio → casos de
/// uso) con respuestas HTTP tomadas de los fixtures del contrato.
/// Margen para que la petición HTTP simulada (asíncrona) termine antes de
/// que bloc_test cierre el bloc.
const _io = Duration(milliseconds: 100);

void main() {
  late ScriptedAdapter adapter;
  late InMemoryAuthLocalDataSource local;
  late SessionExpiredNotifier notifier;
  late Dio dio;

  final login200 = loadFixtureObject('authentication/loginUser.200.json');
  final register201 = loadFixtureObject('authentication/registerUser.201.json');
  final refresh200 = loadFixtureObject('authentication/refreshToken.200.json');

  setUp(() {
    adapter = ScriptedAdapter();
    local = InMemoryAuthLocalDataSource();
    notifier = SessionExpiredNotifier();
    dio = Dio(BaseOptions(baseUrl: 'http://localhost:4010/api/v1'))
      ..httpClientAdapter = adapter;
    dio.interceptors.add(TokenInterceptor(
      localDataSource: local,
      dio: dio,
      sessionExpiredNotifier: notifier,
    ));
  });

  tearDown(() => notifier.dispose());

  AuthBloc buildBloc() {
    final repository = AuthRepositoryImpl(
      remoteDataSource: AuthRemoteDataSourceImpl(dio: dio),
      localDataSource: local,
    );
    return AuthBloc(
      loginUseCase: LoginUseCase(repository),
      registerUseCase: RegisterUseCase(repository),
      logoutUseCase: LogoutUseCase(repository),
      restoreSessionUseCase: RestoreSessionUseCase(repository),
      sessionExpiredNotifier: notifier,
    );
  }

  const login = LoginRequested(email: 'alex@vito.com', password: 'vito-dev-2026');

  group('login', () {
    blocTest<AuthBloc, AuthState>(
      'éxito: autentica con el usuario del contrato y guarda los tokens',
      setUp: () => adapter.on('POST /auth/login', [StubResponse(200, login200)]),
      build: buildBloc,
      wait: _io,
      act: (bloc) => bloc.add(login),
      expect: () => [
        const AuthLoading(),
        isA<AuthAuthenticated>()
            .having((s) => s.user.id, 'id', 'usr_01H8X2K4QJ')
            .having((s) => s.user.profile?.displayName, 'displayName', 'Alex Villa')
            .having((s) => s.user.profile?.avatar, 'avatar',
                'https://cdn.vito.com/usr_01H8X2K4QJ/avatar.jpg'),
      ],
      verify: (_) {
        expect(local.accessToken, login200['accessToken']);
        expect(local.refreshToken, login200['refreshToken']);
        expect(local.userId, 'usr_01H8X2K4QJ');
      },
    );

    blocTest<AuthBloc, AuthState>(
      '401: muestra el mensaje del backend y no intenta refresh',
      setUp: () => adapter.on('POST /auth/login', [
        StubResponse(401, loadFixtureObject('authentication/loginUser.401.json')),
      ]),
      build: buildBloc,
      wait: _io,
      act: (bloc) => bloc.add(login),
      expect: () => [
        const AuthLoading(),
        const AuthError(message: 'Credenciales inválidas'),
      ],
      verify: (_) {
        expect(adapter.count('POST /auth/refresh'), 0);
        expect(local.accessToken, isNull);
      },
    );

    blocTest<AuthBloc, AuthState>(
      '400: muestra el mensaje de validación',
      setUp: () => adapter.on('POST /auth/login', [
        StubResponse(400, loadFixtureObject('authentication/loginUser.400.json')),
      ]),
      build: buildBloc,
      wait: _io,
      act: (bloc) => bloc.add(login),
      expect: () => [
        const AuthLoading(),
        const AuthError(message: 'El campo password es obligatorio'),
      ],
    );

    blocTest<AuthBloc, AuthState>(
      'sin red: error y sin tokens',
      setUp: () => adapter.on('POST /auth/login', []),
      build: buildBloc,
      wait: _io,
      act: (bloc) => bloc.add(login),
      expect: () => [const AuthLoading(), isA<AuthError>()],
      verify: (_) => expect(local.accessToken, isNull),
    );
  });

  group('register', () {
    blocTest<AuthBloc, AuthState>(
      'éxito: envía displayName y guarda los tokens',
      setUp: () =>
          adapter.on('POST /auth/register', [StubResponse(201, register201)]),
      build: buildBloc,
      wait: _io,
      act: (bloc) => bloc.add(const RegisterRequested(
        username: 'alex_villa',
        email: 'alex@vito.com',
        password: 'vito-dev-2026',
        displayName: 'Alex Villa',
      )),
      expect: () => [const AuthLoading(), isA<AuthAuthenticated>()],
      verify: (_) {
        expect(jsonDecode(adapter.bodies.single),
            loadFixtureObject('authentication/registerUser.request.json'));
        expect(local.accessToken, register201['accessToken']);
      },
    );

    blocTest<AuthBloc, AuthState>(
      'sin displayName no lo envía (el contrato no admite null)',
      setUp: () =>
          adapter.on('POST /auth/register', [StubResponse(201, register201)]),
      build: buildBloc,
      wait: _io,
      act: (bloc) => bloc.add(const RegisterRequested(
        username: 'alex_villa',
        email: 'alex@vito.com',
        password: 'vito-dev-2026',
      )),
      expect: () => [const AuthLoading(), isA<AuthAuthenticated>()],
      verify: (_) => expect(
        (jsonDecode(adapter.bodies.single) as Map).containsKey('displayName'),
        isFalse,
      ),
    );

    blocTest<AuthBloc, AuthState>(
      '409: muestra el conflicto del backend',
      setUp: () => adapter.on('POST /auth/register', [
        StubResponse(409, loadFixtureObject('authentication/registerUser.409.json')),
      ]),
      build: buildBloc,
      wait: _io,
      act: (bloc) => bloc.add(const RegisterRequested(
        username: 'alex_villa',
        email: 'alex@vito.com',
        password: 'vito-dev-2026',
      )),
      expect: () => [
        const AuthLoading(),
        const AuthError(message: 'El email ya está registrado'),
      ],
    );
  });

  group('auto-login (AuthCheckRequested)', () {
    blocTest<AuthBloc, AuthState>(
      'con refresh token guardado: renueva y autentica',
      setUp: () {
        local.refreshToken = 'refresh-guardado';
        adapter.on('POST /auth/refresh', [StubResponse(200, refresh200)]);
      },
      build: buildBloc,
      wait: _io,
      act: (bloc) => bloc.add(const AuthCheckRequested()),
      expect: () => [
        isA<AuthAuthenticated>()
            .having((s) => s.user.username, 'username', 'alex_villa'),
      ],
      verify: (_) {
        expect(jsonDecode(adapter.bodies.single),
            {'refreshToken': 'refresh-guardado'});
        expect(local.accessToken, refresh200['accessToken']);
        expect(local.refreshToken, refresh200['refreshToken']);
      },
    );

    blocTest<AuthBloc, AuthState>(
      'sin sesión guardada: no llama al backend',
      build: buildBloc,
      wait: _io,
      act: (bloc) => bloc.add(const AuthCheckRequested()),
      expect: () => [const AuthUnauthenticated()],
      verify: (_) => expect(adapter.requests, isEmpty),
    );

    blocTest<AuthBloc, AuthState>(
      'refresh 401: limpia la sesión guardada',
      setUp: () {
        local
          ..accessToken = 'viejo'
          ..refreshToken = 'revocado';
        adapter.on('POST /auth/refresh', [
          StubResponse(401, loadFixtureObject('authentication/refreshToken.401.json')),
        ]);
      },
      build: buildBloc,
      wait: _io,
      act: (bloc) => bloc.add(const AuthCheckRequested()),
      expect: () => [const AuthUnauthenticated()],
      verify: (_) {
        expect(local.accessToken, isNull);
        expect(local.refreshToken, isNull);
      },
    );

    blocTest<AuthBloc, AuthState>(
      'sin red: no autentica pero conserva la sesión para reintentar',
      setUp: () {
        local.refreshToken = 'guardado';
        adapter.on('POST /auth/refresh', []);
      },
      build: buildBloc,
      wait: _io,
      act: (bloc) => bloc.add(const AuthCheckRequested()),
      expect: () => [const AuthUnauthenticated()],
      verify: (_) => expect(local.refreshToken, 'guardado'),
    );
  });

  group('logout', () {
    blocTest<AuthBloc, AuthState>(
      '204: cierra la sesión y borra los tokens',
      setUp: () {
        local
          ..accessToken = 'acceso'
          ..refreshToken = 'refresh';
        adapter.on('POST /auth/logout', [const StubResponse(204)]);
      },
      build: buildBloc,
      wait: _io,
      act: (bloc) => bloc.add(const LogoutRequested()),
      expect: () => [const AuthLoading(), const AuthUnauthenticated()],
      verify: (_) {
        expect(adapter.requests.single.headers['Authorization'], 'Bearer acceso');
        expect(local.accessToken, isNull);
        expect(local.refreshToken, isNull);
      },
    );

    blocTest<AuthBloc, AuthState>(
      'backend rechaza el logout: la sesión local se cierra igual',
      setUp: () {
        local
          ..accessToken = 'vencido'
          ..refreshToken = 'refresh';
        adapter.on('POST /auth/logout', [
          StubResponse(401, loadFixtureObject('authentication/logoutUser.401.json')),
        ]);
      },
      build: buildBloc,
      wait: _io,
      act: (bloc) => bloc.add(const LogoutRequested()),
      expect: () => [const AuthLoading(), const AuthUnauthenticated()],
      verify: (_) => expect(local.accessToken, isNull),
    );
  });

  blocTest<AuthBloc, AuthState>(
    'refresh fallido en otra petición: pasa a no autenticado',
    setUp: () {
      local
        ..accessToken = 'vencido'
        ..refreshToken = 'revocado';
      adapter
        ..on('GET /events', [
          StubResponse(401, loadFixtureObject('events/listEvents.401.json')),
        ])
        ..on('POST /auth/refresh', [
          StubResponse(401, loadFixtureObject('authentication/refreshToken.401.json')),
        ]);
    },
    build: buildBloc,
    wait: _io,
    seed: () => const AuthLoading(),
    act: (_) async {
      await expectLater(dio.get('/events'), throwsA(isA<DioException>()));
    },
    expect: () => [const AuthUnauthenticated()],
    verify: (_) => expect(local.refreshToken, isNull),
  );
}
