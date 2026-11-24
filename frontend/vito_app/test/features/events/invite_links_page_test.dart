import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'package:vito_app/features/events/domain/usecases/create_invite_link.dart';
import 'package:vito_app/features/events/domain/usecases/list_invite_links.dart';
import 'package:vito_app/features/events/domain/usecases/revoke_invite_link.dart';
import 'package:vito_app/features/events/presentation/bloc/invite_links_cubit.dart';
import 'package:vito_app/features/events/presentation/pages/invite_links_page.dart';

import 'fakes.dart';

final _shared = <String>[];

Future<void> _pump(WidgetTester tester, FakeEventRepository repository) async {
  tester.view.physicalSize = const Size(800, 2000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [Locale('es')],
      locale: const Locale('es'),
      home: BlocProvider(
        create: (_) => InviteLinksCubit(
          eventId: 'evt_1',
          listInviteLinks: ListInviteLinksUseCase(repository),
          createInviteLink: CreateInviteLinkUseCase(repository),
          revokeInviteLink: RevokeInviteLinkUseCase(repository),
        ),
        child: InviteLinksPage(
          eventTitle: 'Los 30 de Marta',
          share: (text) async => _shared.add(text),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

/// T042: el organizador crea, comparte y revoca enlaces, con usos y estado.
void main() {
  setUpAll(() async {
    await initializeDateFormatting('es');
  });

  setUp(_shared.clear);

  testWidgets('sin enlaces ofrece crear uno y lo muestra listo para compartir',
      (tester) async {
    final repository = FakeEventRepository();
    await _pump(tester, repository);

    expect(find.text('Aún no has creado enlaces para este evento'),
        findsOneWidget);

    await tester.tap(find.text('Crear enlace de invitación'));
    await tester.pumpAndSettle();

    expect(repository.createInviteLinkCalls, 1);
    expect(find.text('Enlace creado: ya puedes compartirlo'), findsOneWidget);
    expect(find.text('Activo'), findsOneWidget);
    expect(find.textContaining('0 usos'), findsOneWidget);
  });

  testWidgets('"Enviar por WhatsApp" comparte la url real del enlace',
      (tester) async {
    final repository = FakeEventRepository(inviteLinks: [fakeInviteLink()]);
    await _pump(tester, repository);

    await tester.tap(find.text('Enviar por WhatsApp'));
    await tester.pumpAndSettle();

    expect(_shared, [
      'Te invito a «Los 30 de Marta» en ViTo: '
          'https://app.vitoevents.app/i/k3J9xQ2mV7pL1sT8wY4zBc',
    ]);
  });

  testWidgets('muestra usos y estado; los no vigentes no se comparten',
      (tester) async {
    final repository = FakeEventRepository(inviteLinks: [
      fakeInviteLink(id: 'inl_1', uses: 3, maxUses: 50),
      fakeInviteLink(
        id: 'inl_2',
        token: 'm8N2pR5tV9wX1yZ3aB4cDe',
        revoked: true,
        createdAt: DateTime.utc(2026, 11, 19),
      ),
    ]);
    await _pump(tester, repository);

    expect(find.textContaining('3 de 50 usos'), findsOneWidget);
    expect(find.text('Activo'), findsOneWidget);
    expect(find.text('Revocado'), findsOneWidget);
    expect(find.text('Enviar por WhatsApp'), findsOneWidget);
  });

  testWidgets('revocar pide confirmación y marca el enlace como revocado',
      (tester) async {
    final repository = FakeEventRepository(inviteLinks: [fakeInviteLink()]);
    await _pump(tester, repository);

    await tester.tap(find.text('Revocar'));
    await tester.pumpAndSettle();
    expect(find.text('¿Revocar el enlace?'), findsOneWidget);

    await tester.tap(find.text('Revocar').last);
    await tester.pumpAndSettle();

    expect(repository.revokedLinkIds, ['inl_1']);
    expect(find.text('Revocado'), findsOneWidget);
    expect(find.text('Enviar por WhatsApp'), findsNothing);
  });
}
