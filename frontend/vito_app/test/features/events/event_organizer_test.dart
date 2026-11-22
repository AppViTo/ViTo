import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/features/auth/presentation/bloc/auth_bloc.dart';
import 'package:vito_app/features/events/domain/entities/attendee.dart';
import 'package:vito_app/features/events/domain/entities/event.dart';
import 'package:vito_app/features/events/domain/entities/invitation.dart';
import 'package:vito_app/features/events/domain/entities/rsvp.dart';
import 'package:vito_app/features/events/domain/usecases/list_event_attendees.dart';
import 'package:vito_app/features/events/domain/usecases/list_event_invitations.dart';
import 'package:vito_app/features/events/presentation/bloc/event_bloc.dart';
import 'package:vito_app/features/events/presentation/bloc/event_guests_cubit.dart';
import 'package:vito_app/features/events/presentation/pages/event_detail_page.dart';
import 'package:vito_app/features/events/presentation/pages/event_guests_page.dart';

import 'auth_stub.dart';
import 'fakes.dart';

Event _event({EventStatus status = EventStatus.published}) => Event(
      id: 'evt_1',
      organizerId: 'usr_1',
      title: 'Los 30 de Marta',
      status: status,
      settings: const EventSettings(isPublic: false),
      details: EventDetails(
        startDate: DateTime.now().add(const Duration(days: 30)),
        location: 'Medellín',
      ),
      stats: const EventStats(
        totalInvited: 10,
        totalAccepted: 6,
        totalDeclined: 1,
        totalMaybe: 2,
      ),
    );

const _localizations = [
  GlobalMaterialLocalizations.delegate,
  GlobalWidgetsLocalizations.delegate,
  GlobalCupertinoLocalizations.delegate,
];

final _opened = <RouteSettings>[];

Future<void> _pumpDetail(
  WidgetTester tester,
  FakeEventRepository repository, {
  required String? userId,
}) async {
  tester.view.physicalSize = const Size(800, 2600);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  _opened.clear();
  await tester.pumpWidget(
    MultiBlocProvider(
      providers: [
        BlocProvider<AuthBloc>(create: (_) => StubAuthBloc(userId)),
        BlocProvider<EventBloc>(
          create: (_) => EventBloc(eventRepository: repository),
        ),
      ],
      child: MaterialApp(
        localizationsDelegates: _localizations,
        supportedLocales: const [Locale('es')],
        locale: const Locale('es'),
        home: const EventDetailPage(eventId: 'evt_1'),
        onGenerateRoute: (settings) {
          _opened.add(settings);
          return MaterialPageRoute(builder: (_) => const SizedBox());
        },
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _pumpGuests(
  WidgetTester tester,
  FakeEventRepository repository,
) async {
  await tester.pumpWidget(
    MaterialApp(
      localizationsDelegates: _localizations,
      supportedLocales: const [Locale('es')],
      locale: const Locale('es'),
      home: BlocProvider(
        create: (_) => EventGuestsCubit(
          eventId: 'evt_1',
          listAttendees: ListEventAttendeesUseCase(repository),
          listInvitations: ListEventInvitationsUseCase(repository),
        ),
        child: const EventGuestsPage(eventTitle: 'Los 30 de Marta'),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

/// T052, T055 y T058: lo que el organizador gestiona desde el detalle y lo
/// que ven los invitados.
void main() {
  setUpAll(() async {
    await initializeDateFormatting('es');
  });

  group('detalle', () {
    testWidgets('el invitado ve solo "N personas confirmadas"', (tester) async {
      await _pumpDetail(
        tester,
        FakeEventRepository(eventDetail: _event()),
        userId: 'usr_2',
      );

      expect(find.text('6 personas confirmadas'), findsOneWidget);
      expect(find.text('Estadísticas'), findsNothing);
      expect(find.text('Ver invitados'), findsNothing);
      expect(find.text('Cancelar evento'), findsNothing);
    });

    testWidgets('el organizador ve las estadísticas y abre sus invitados',
        (tester) async {
      await _pumpDetail(
        tester,
        FakeEventRepository(eventDetail: _event()),
        userId: 'usr_1',
      );

      expect(find.text('Estadísticas'), findsOneWidget);
      expect(find.text('Invitados'), findsOneWidget);
      expect(find.textContaining('personas confirmadas'), findsNothing);
      expect(find.text('Rechazados'), findsOneWidget);

      await tester.tap(find.text('Ver invitados'));
      await tester.pumpAndSettle();

      expect(_opened.single.name, '/event-guests');
      expect(_opened.single.arguments,
          {'eventId': 'evt_1', 'eventTitle': 'Los 30 de Marta'});
    });

    testWidgets('cancelar pide confirmación y cancela el evento',
        (tester) async {
      final repository = FakeEventRepository(eventDetail: _event());
      await _pumpDetail(tester, repository, userId: 'usr_1');

      await tester.tap(find.text('Cancelar evento'));
      await tester.pumpAndSettle();
      expect(find.text('¿Cancelar el evento?'), findsOneWidget);

      await tester.tap(find.text('Volver'));
      await tester.pumpAndSettle();
      expect(repository.cancelledEventIds, isEmpty);

      await tester.tap(find.text('Cancelar evento'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Sí, cancelar'));
      await tester.pumpAndSettle();

      expect(repository.cancelledEventIds, ['evt_1']);
      expect(find.text('Evento cancelado'), findsOneWidget);
      // Se recarga el detalle y sigue en pantalla.
      expect(repository.getEventCalls, 2);
      expect(find.text('Los 30 de Marta'), findsOneWidget);
    });

    testWidgets('un 409 al cancelar muestra el mensaje del backend',
        (tester) async {
      final repository = FakeEventRepository(
        eventDetail: _event(),
        cancelFailure: const ConflictFailure(
            message: 'No se puede cancelar un evento que ya terminó'),
      );
      await _pumpDetail(tester, repository, userId: 'usr_1');

      await tester.tap(find.text('Cancelar evento'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Sí, cancelar'));
      await tester.pumpAndSettle();

      expect(find.text('No se puede cancelar un evento que ya terminó'),
          findsOneWidget);
      expect(find.text('Los 30 de Marta'), findsOneWidget);
    });

    testWidgets('un evento cancelado se ve "Cancelado" y no se puede volver '
        'a cancelar ni editar', (tester) async {
      await _pumpDetail(
        tester,
        FakeEventRepository(eventDetail: _event(status: EventStatus.cancelled)),
        userId: 'usr_1',
      );

      expect(find.text('Cancelado'), findsOneWidget);
      for (final label in const ['Cancelar evento', 'Editar evento']) {
        final button = tester.widget<ButtonStyleButton>(find.ancestor(
          of: find.text(label),
          matching: find.byWidgetPredicate((w) => w is ButtonStyleButton),
        ));
        expect(button.onPressed, isNull, reason: label);
      }
    });
  });

  group('invitados del organizador', () {
    testWidgets('lista asistentes con su estado y sin correo', (tester) async {
      await _pumpGuests(
        tester,
        FakeEventRepository(
          attendees: [
            Attendee(
              userId: 'usr_2',
              displayName: 'Carolina Martínez',
              status: RSVPStatus.accepted,
              respondedAt: DateTime(2026, 10, 28),
            ),
            const Attendee(userId: 'usr_3', status: RSVPStatus.waitlisted),
          ],
          invitations: const [],
        ),
      );

      expect(find.text('Carolina Martínez'), findsOneWidget);
      expect(find.text('Va · respondió el 28 de octubre'), findsOneWidget);
      expect(find.text('Invitado sin nombre'), findsOneWidget);
      expect(find.text('En lista de espera'), findsOneWidget);
    });

    testWidgets('lista invitaciones con su origen y el nombre si respondió',
        (tester) async {
      await _pumpGuests(
        tester,
        FakeEventRepository(
          attendees: const [
            Attendee(
              userId: 'usr_2',
              displayName: 'Carolina Martínez',
              status: RSVPStatus.accepted,
            ),
          ],
          invitations: [
            Invitation(
              id: 'inv_1',
              eventId: 'evt_1',
              inviterId: 'usr_1',
              inviteeId: 'usr_2',
              status: InvitationStatus.accepted,
              inviteLinkId: 'inl_1',
              sentAt: DateTime(2026, 10, 27),
            ),
            Invitation(
              id: 'inv_2',
              eventId: 'evt_1',
              inviterId: 'usr_1',
              inviteeId: 'usr_9',
              status: InvitationStatus.pending,
              sentAt: DateTime(2026, 10, 28),
            ),
          ],
        ),
      );

      await tester.tap(find.text('Invitaciones'));
      await tester.pumpAndSettle();

      expect(find.text('Carolina Martínez'), findsOneWidget);
      expect(find.text('Aceptada · por enlace · enviada el 27 de octubre'),
          findsOneWidget);
      expect(find.text('Invitado'), findsOneWidget);
      expect(find.text('Pendiente · desde la app · enviada el 28 de octubre'),
          findsOneWidget);
    });

    testWidgets('un 403 muestra el mensaje del backend y permite reintentar',
        (tester) async {
      await _pumpGuests(
        tester,
        FakeEventRepository(
          attendeesFailure: const AuthorizationFailure(
              message: 'Solo el organizador puede ver la lista de asistentes'),
          invitations: const [],
        ),
      );

      expect(find.text('Solo el organizador puede ver la lista de asistentes'),
          findsOneWidget);
      expect(find.text('Reintentar'), findsOneWidget);
    });

    testWidgets('sin datos muestra los estados vacíos en español',
        (tester) async {
      await _pumpGuests(
        tester,
        FakeEventRepository(attendees: const [], invitations: const []),
      );

      expect(find.text('Aún nadie ha respondido'), findsOneWidget);
      await tester.tap(find.text('Invitaciones'));
      await tester.pumpAndSettle();
      expect(find.text('Aún no has invitado a nadie'), findsOneWidget);
    });
  });
}
