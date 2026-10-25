import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/features/auth/data/datasources/auth_remote_datasource.dart';
import 'package:vito_app/features/auth/data/repositories/auth_repository_impl.dart';
import 'package:vito_app/features/auth/domain/usecases/login.dart';
import 'package:vito_app/features/auth/domain/usecases/logout.dart';
import 'package:vito_app/features/auth/domain/usecases/register.dart';
import 'package:vito_app/features/auth/domain/usecases/restore_session.dart';
import 'package:vito_app/features/auth/presentation/bloc/auth_bloc.dart';
import 'package:vito_app/features/splash/presentation/pages/splash_page.dart';

import '../../contract/fixture_loader.dart';
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

/// El splash decide la primera pantalla con el resultado del
/// `AuthCheckRequested` que despacha `ViToApp`: sesión restaurada → home,
/// sin sesión (o refresh rechazado/sin red) → login.
void main() {
  late ScriptedAdapter adapter;
  late InMemoryAuthLocalDataSource local;
  late Dio dio;
  late AuthBloc bloc;
  late _RecordingNavigatorObserver observer;

  final refresh200 = loadFixtureObject('authentication/refreshToken.200.json');
  final refresh401 = loadFixtureObject('authentication/refreshToken.401.json');

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
    );
  }

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
    adapter = ScriptedAdapter();
    local = InMemoryAuthLocalDataSource();
    dio = Dio(BaseOptions(baseUrl: 'http://localhost:4010/api/v1'))
      ..httpClientAdapter = adapter;
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
    local.accessToken = 'access-vigente';
    local.refreshToken = 'refresh-vigente';
    adapter.on('POST /auth/refresh', [StubResponse(200, refresh200)]);
    bloc = buildBloc();

    await pumpSplash(tester);

    expect(observer.pushed, contains('/home'));
    expect(observer.pushed, isNot(contains('/login')));
  });

  testWidgets('sin sesión guardada entra a login', (tester) async {
    bloc = buildBloc();

    await pumpSplash(tester);

    expect(observer.pushed, contains('/login'));
    expect(observer.pushed, isNot(contains('/home')));
  });

  testWidgets('refresh rechazado (401) limpia los tokens y entra a login',
      (tester) async {
    local.accessToken = 'access-viejo';
    local.refreshToken = 'refresh-viejo';
    adapter.on('POST /auth/refresh', [StubResponse(401, refresh401)]);
    bloc = buildBloc();

    await pumpSplash(tester);

    expect(observer.pushed, contains('/login'));
    expect(local.accessToken, isNull);
    expect(local.refreshToken, isNull);
  });

  testWidgets('refresh sin conexión (error inesperado) entra a login',
      (tester) async {
    // Sin respuesta preparada para la ruta, el adaptador falla: el
    // repositorio lo pliega a Left y el splash no debe quedarse pensando.
    local.accessToken = 'access-vigente';
    local.refreshToken = 'refresh-vigente';
    bloc = buildBloc();

    await pumpSplash(tester);

    expect(observer.pushed, contains('/login'));
  });
}
