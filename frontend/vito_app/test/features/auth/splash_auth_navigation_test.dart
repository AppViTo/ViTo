import 'package:dartz/dartz.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/core/error/failures.dart';

import 'package:vito_app/features/auth/domain/usecases/login.dart';
import 'package:vito_app/features/auth/domain/usecases/logout.dart';
import 'package:vito_app/features/auth/domain/usecases/register.dart';
import 'package:vito_app/features/auth/domain/usecases/restore_session.dart';
import 'package:vito_app/features/auth/presentation/bloc/auth_bloc.dart';
import 'package:vito_app/features/splash/presentation/pages/splash_page.dart';

import 'fakes.dart';

class _RecordingNavigatorObserver extends NavigatorObserver {
  final List<String?> pushed = <String?>[];

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    pushed.add(route.settings.name);
  }

  /// `Navigator.pushReplacementNamed` registra la ruta nueva con `didReplace`
  /// (no con `didPush`).
  @override
  void didReplace({Route<dynamic>? newRoute, Route<dynamic>? oldRoute}) {
    pushed.add(newRoute?.settings.name);
  }
}

void main() {
  late FakeAuthRepository repository;
  late AuthBloc bloc;
  late _RecordingNavigatorObserver observer;

  AuthBloc buildBloc() => AuthBloc(
        loginUseCase: LoginUseCase(repository),
        registerUseCase: RegisterUseCase(repository),
        logoutUseCase: LogoutUseCase(repository),
        restoreSessionUseCase: RestoreSessionUseCase(repository),
      );

  Future<void> pumpSplash(WidgetTester tester) async {
    await tester.pumpWidget(
      BlocProvider<AuthBloc>.value(
        value: bloc,
        child: MaterialApp(
          navigatorObservers: [observer],
          initialRoute: '/',
          routes: <String, WidgetBuilder>{
            '/': (_) => const SplashPage(),
            '/login': (_) => const Scaffold(body: Text('LOGIN')),
            '/home': (_) => const Scaffold(body: Text('HOME')),
          },
        ),
      ),
    );

    bloc.add(const AuthCheckRequested());

    // El splash encadena delays de ~3.6 s antes de decidir; además el
    // AuthCheck puede resolver después. Se avanza el reloj en pasos.
    for (var i = 0; i < 6; i++) {
      await tester.pump(const Duration(seconds: 1));
    }
  }

  setUp(() {
    repository = FakeAuthRepository();
    observer = _RecordingNavigatorObserver();
  });

  // El bloc se construye DENTRO del body de `testWidgets`: al crearse en la
  // zona FakeAsync, el `await` del handler de AuthCheck reanuda con los pumps.
  // Creado en `setUp` (zona real) su continuación nunca corre y el estado se
  // queda en AuthInitial.

  tearDown(() async {
    await bloc.close();
  });

  testWidgets('con sesión restaurada (refresh 200) entra a home',
      (tester) async {
    repository.restoreOutcome = const Right(FakeAuthRepository.testUser);
    bloc = buildBloc();

    await pumpSplash(tester);

    expect(observer.pushed, contains('/home'));
    expect(observer.pushed, isNot(contains('/login')));
  });

  testWidgets('sin sesión guardada entra a login', (tester) async {
    repository.restoreOutcome = const Right(null);
    bloc = buildBloc();

    await pumpSplash(tester);

    expect(observer.pushed, contains('/login'));
    expect(observer.pushed, isNot(contains('/home')));
  });

  testWidgets('refresh rechazado (401 → Right(null)) entra a login',
      (tester) async {
    // El repositorio limpia los tokens y devuelve Right(null); el splash no
    // debe quedarse pensando.
    repository.restoreOutcome = const Right(null);
    bloc = buildBloc();

    await pumpSplash(tester);

    expect(observer.pushed, contains('/login'));
  });

  testWidgets('sin conexión (Left) entra a login para reintentar',
      (tester) async {
    repository.restoreOutcome = const Left(
      NetworkFailure(message: 'Network error. Please try again.'),
    );
    bloc = buildBloc();

    await pumpSplash(tester);

    expect(observer.pushed, contains('/login'));
  });
}
