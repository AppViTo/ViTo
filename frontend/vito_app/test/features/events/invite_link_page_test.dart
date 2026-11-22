import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'package:vito_app/config/routes/app_router.dart';
import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/core/navigation/pending_invite_link.dart';
import 'package:vito_app/features/auth/presentation/bloc/auth_bloc.dart';
import 'package:vito_app/features/events/domain/entities/invite_link.dart';
import 'package:vito_app/features/events/domain/usecases/accept_invite_link.dart';
import 'package:vito_app/features/events/domain/usecases/get_invite_link_preview.dart';
import 'package:vito_app/features/events/presentation/bloc/invite_link_cubit.dart';
import 'package:vito_app/features/events/presentation/pages/invite_link_page.dart';

import 'auth_stub.dart';
import 'fakes.dart';

const _token = 'k3J9xQ2mV7pL1sT8wY4zBc';

final _preview = InviteLinkPreview(
  eventId: 'evt_01H8X2K4QJ',
  title: 'Cumple de Caro',
  organizerDisplayName: 'Bernardo',
  startDate: DateTime.utc(2026, 12, 26, 1),
  location: 'Cartagena de Indias',
  expiresAt: DateTime.utc(2026, 12, 3, 15),
);

/// Rutas que se abrieron desde la página, con sus argumentos.
final _opened = <RouteSettings>[];

Future<void> _pump(
  WidgetTester tester,
  FakeEventRepository repository, {
  String? userId,
}) async {
  final page = BlocProvider(
    create: (_) => InviteLinkCubit(
      token: _token,
      getPreview: GetInviteLinkPreviewUseCase(repository),
      acceptInviteLink: AcceptInviteLinkUseCase(repository),
    ),
    child: const InviteLinkPage(token: _token),
  );
  await tester.pumpWidget(
    BlocProvider<AuthBloc>(
      create: (_) => StubAuthBloc(userId),
      child: MaterialApp(
        localizationsDelegates: const [
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: const [Locale('es')],
        locale: const Locale('es'),
        home: page,
        onGenerateRoute: (settings) {
          _opened.add(settings);
          return MaterialPageRoute(
            builder: (_) => Scaffold(body: Text('ruta ${settings.name}')),
          );
        },
      ),
    ),
  );
  await tester.pumpAndSettle();
}

/// T043: `/i/<token>` con vista previa sin sesión, vuelta tras el login,
/// unión con sesión y mensaje único en 404.
void main() {
  setUpAll(() async {
    await initializeDateFormatting('es');
  });

  setUp(() {
    _opened.clear();
    PendingInviteLink.clear();
  });

  testWidgets('sin sesión muestra la invitación y lleva al login recordándola',
      (tester) async {
    final repository = FakeEventRepository(inviteLinkPreview: _preview);
    await _pump(tester, repository);

    expect(find.text('Cumple de Caro'), findsOneWidget);
    expect(find.text('Bernardo te invita a'), findsOneWidget);
    expect(find.text('Cartagena de Indias'), findsOneWidget);
    expect(find.text('Inicia sesión o regístrate para unirte'), findsOneWidget);
    expect(find.text('Unirme al evento'), findsNothing);

    await tester.tap(find.text('Iniciar sesión'));
    await tester.pumpAndSettle();

    expect(_opened.single.name, AppRouter.login);
    expect(PendingInviteLink.take(), _token);
  });

  testWidgets('"Crear cuenta" también recuerda la invitación', (tester) async {
    final repository = FakeEventRepository(inviteLinkPreview: _preview);
    await _pump(tester, repository);

    await tester.tap(find.text('Crear cuenta'));
    await tester.pumpAndSettle();

    expect(_opened.single.name, AppRouter.register);
    expect(PendingInviteLink.take(), _token);
  });

  testWidgets('con sesión, "Unirme" acepta el enlace y abre el evento',
      (tester) async {
    final repository = FakeEventRepository(inviteLinkPreview: _preview);
    await _pump(tester, repository, userId: 'usr_2');

    await tester.tap(find.text('Unirme al evento'));
    await tester.pumpAndSettle();

    expect(repository.acceptedTokens, [_token]);
    expect(_opened.single.name, AppRouter.eventDetail);
    expect(
      (_opened.single.arguments as Map)['eventId'],
      'evt_01H8X2K4QJ',
    );
  });

  testWidgets('un 409 al unirse muestra el mensaje y deja la invitación',
      (tester) async {
    final repository = FakeEventRepository(
      inviteLinkPreview: _preview,
      acceptFailure:
          const ConflictFailure(message: 'El enlace alcanzó su límite de usos'),
    );
    await _pump(tester, repository, userId: 'usr_2');

    await tester.tap(find.text('Unirme al evento'));
    await tester.pumpAndSettle();

    expect(find.text('El enlace alcanzó su límite de usos'), findsOneWidget);
    expect(find.text('Cumple de Caro'), findsOneWidget);
    expect(_opened, isEmpty);
  });

  testWidgets('un enlace inexistente, vencido o revocado da un solo mensaje',
      (tester) async {
    final repository = FakeEventRepository(
      previewFailure: const NotFoundFailure(message: 'Event link revoked'),
    );
    await _pump(tester, repository);

    expect(find.text(inviteLinkUnavailableMessage), findsOneWidget);
    expect(find.text('Event link revoked'), findsNothing);
    expect(find.text('Reintentar'), findsNothing);
  });

  testWidgets('un error de red permite reintentar', (tester) async {
    final repository = FakeEventRepository(
      previewFailure: const NetworkFailure(message: 'Sin conexión'),
    );
    await _pump(tester, repository);

    expect(find.text('Sin conexión'), findsOneWidget);
    expect(find.text('Reintentar'), findsOneWidget);
  });

  group('AppRouter.inviteTokenOf', () {
    test('lee el token de /i/<token>', () {
      expect(AppRouter.inviteTokenOf('/i/$_token'), _token);
      expect(AppRouter.inviteTokenOf('/i/$_token?utm=wa'), _token);
    });

    test('ignora otras rutas', () {
      expect(AppRouter.inviteTokenOf('/'), isNull);
      expect(AppRouter.inviteTokenOf('/i/'), isNull);
      expect(AppRouter.inviteTokenOf('/invite-links'), isNull);
      expect(AppRouter.inviteTokenOf(null), isNull);
    });
  });
}
