import 'package:dartz/dartz.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/features/auth/domain/usecases/login.dart';
import 'package:vito_app/features/auth/domain/usecases/logout.dart';
import 'package:vito_app/features/auth/domain/usecases/register.dart';
import 'package:vito_app/features/auth/domain/usecases/restore_session.dart';
import 'package:vito_app/features/auth/presentation/bloc/auth_bloc.dart';
import 'package:vito_app/core/error/failures.dart';

import 'fakes.dart';

void main() {
  late FakeAuthRepository repository;
  late AuthBloc bloc;

  AuthBloc buildBloc() => AuthBloc(
        loginUseCase: LoginUseCase(repository),
        registerUseCase: RegisterUseCase(repository),
        logoutUseCase: LogoutUseCase(repository),
        restoreSessionUseCase: RestoreSessionUseCase(repository),
      );

  setUp(() {
    repository = FakeAuthRepository();
    bloc = buildBloc();
  });

  tearDown(() async {
    await bloc.close();
  });

  group('LoginRequested', () {
    test('éxito: loading y autenticado con el usuario del backend', () async {
      final expectation = expectLater(
        bloc.stream,
        emitsInOrder(<Object>[
          isA<AuthLoading>(),
          isA<AuthAuthenticated>()
              .having((s) => s.user.id, 'user.id', 'usr_01H8X2K4QJ')
              .having((s) => s.user.username, 'username', 'alex_villa'),
        ]),
      );

      bloc.add(const LoginRequested(
        email: 'alex@vito.com',
        password: 'vito-dev-2026',
      ));

      await expectation;
      expect(repository.calls, contains('login:alex@vito.com'));
    });

    test('401: el backend rechaza credenciales y se muestra el mensaje',
        () async {
      repository.loginOutcome = const Left(
        AuthenticationFailure(message: 'Credenciales inválidas'),
      );

      final expectation = expectLater(
        bloc.stream,
        emitsInOrder(<Object>[
          isA<AuthLoading>(),
          isA<AuthError>()
              .having((s) => s.message, 'message', 'Credenciales inválidas'),
        ]),
      );

      bloc.add(const LoginRequested(
        email: 'alex@vito.com',
        password: 'mal-password',
      ));

      await expectation;
    });
  });

  group('RegisterRequested', () {
    test('400: error de validación con el mensaje del backend', () async {
      repository.registerOutcome = const Left(
        ValidationFailure(message: 'El campo email no tiene un formato válido'),
      );

      final expectation = expectLater(
        bloc.stream,
        emitsInOrder(<Object>[
          isA<AuthLoading>(),
          isA<AuthError>().having(
            (s) => s.message,
            'message',
            'El campo email no tiene un formato válido',
          ),
        ]),
      );

      bloc.add(const RegisterRequested(
        username: 'alex_villa',
        email: 'no-es-email',
        password: 'vito-dev-2026',
      ));

      await expectation;
      expect(repository.calls, contains('register:alex_villa'));
    });

    test('éxito: registro entra directo a autenticado', () async {
      final expectation = expectLater(
        bloc.stream,
        emitsInOrder(<Object>[
          isA<AuthLoading>(),
          isA<AuthAuthenticated>(),
        ]),
      );

      bloc.add(const RegisterRequested(
        username: 'alex_villa',
        email: 'alex@vito.com',
        password: 'vito-dev-2026',
      ));

      await expectation;
    });
  });

  group('AuthCheckRequested (auto-login con refresh token)', () {
    test('refresh válido: autentica con el user del AuthResponse', () async {
      repository.restoreOutcome = const Right(FakeAuthRepository.testUser);

      final expectation = expectLater(
        bloc.stream,
        emits(isA<AuthAuthenticated>()
            .having((s) => s.user.id, 'user.id', 'usr_01H8X2K4QJ')),
      );

      bloc.add(const AuthCheckRequested());

      await expectation;
      expect(repository.calls, contains('restoreSession'));
    });

    test('sin sesión guardada: no autenticado', () async {
      repository.restoreOutcome = const Right(null);

      final expectation = expectLater(
        bloc.stream,
        emits(isA<AuthUnauthenticated>()),
      );

      bloc.add(const AuthCheckRequested());

      await expectation;
    });

    test('refresh token rechazado (401): no autenticado, sin error',
        () async {
      // El repositorio traduce el 401 del refresh en Right(null) tras limpiar
      // los tokens guardados.
      repository.restoreOutcome = const Right(null);

      final expectation = expectLater(
        bloc.stream,
        emits(isA<AuthUnauthenticated>()),
      );

      bloc.add(const AuthCheckRequested());

      await expectation;
    });

    test('sin conexión: cae a no autenticado para reintentar a mano',
        () async {
      repository.restoreOutcome = const Left(
        NetworkFailure(message: 'Network error. Please try again.'),
      );

      final expectation = expectLater(
        bloc.stream,
        emits(isA<AuthUnauthenticated>()),
      );

      bloc.add(const AuthCheckRequested());

      await expectation;
    });
  });

  group('LogoutRequested', () {
    test('cierra la sesión', () async {
      final expectation = expectLater(
        bloc.stream,
        emitsInOrder(<Object>[
          isA<AuthLoading>(),
          isA<AuthUnauthenticated>(),
        ]),
      );

      bloc.add(const LogoutRequested());

      await expectation;
      expect(repository.calls, contains('logout'));
    });
  });
}
