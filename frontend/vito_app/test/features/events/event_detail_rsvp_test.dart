import 'package:dartz/dartz.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/features/events/domain/entities/event.dart';
import 'package:vito_app/features/events/domain/entities/rsvp.dart';
import 'package:vito_app/features/events/presentation/bloc/event_bloc.dart';
import 'package:vito_app/features/events/presentation/pages/event_detail_page.dart';

import 'fakes.dart';

Event _event({
  EventStatus status = EventStatus.published,
  DateTime? startDate,
}) =>
    Event(
      id: 'evt_1',
      organizerId: 'usr_1',
      title: 'Los 30 de Marta',
      status: status,
      settings: const EventSettings(isPublic: true),
      details: EventDetails(
        startDate: startDate ?? DateTime.now().add(const Duration(days: 30)),
        location: 'Medellín',
      ),
      stats: const EventStats(totalAccepted: 6),
    );

Future<void> _pump(WidgetTester tester, FakeEventRepository repository) async {
  // Pantalla alta: los botones de RSVP quedan visibles sin desplazar, y el
  // toque no cae bajo la barra superior fija.
  tester.view.physicalSize = const Size(800, 2400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [Locale('es'), Locale('en')],
      locale: const Locale('es'),
      home: BlocProvider<EventBloc>(
        create: (_) => EventBloc(eventRepository: repository),
        child: const EventDetailPage(eventId: 'evt_1'),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _tapButton(WidgetTester tester, String label) async {
  final button = find.ancestor(
    of: find.text(label).last,
    matching: find.byWidgetPredicate((w) => w is ButtonStyleButton),
  );
  await tester.tap(button);
  await tester.pumpAndSettle();
}

ButtonStyleButton _button(WidgetTester tester, String label) =>
    tester.widget<ButtonStyleButton>(find.ancestor(
      of: find.text(label).last,
      matching: find.byWidgetPredicate((w) => w is ButtonStyleButton),
    ));

/// T033: "Voy", "No voy", "Tal vez", estado actual, lista de espera y
/// botones deshabilitados en eventos cancelados o pasados.
void main() {
  setUpAll(() async {
    await initializeDateFormatting('es');
  });

  testWidgets('"Voy" envía ACCEPTED y muestra la respuesta sin perder el detalle',
      (tester) async {
    final repository = FakeEventRepository(eventDetail: _event());
    await _pump(tester, repository);

    await _tapButton(tester, 'Voy');

    expect(repository.rsvpCalls.single.$1, 'evt_1');
    expect(repository.rsvpCalls.single.$2, RSVPStatus.accepted);
    expect(find.text('Tu respuesta: Voy'), findsOneWidget);
    expect(find.text('¡Listo! Confirmaste que vas'), findsOneWidget);
    // El detalle sigue en pantalla y se recarga para actualizar contadores.
    expect(find.text('Los 30 de Marta'), findsOneWidget);
    expect(repository.getEventCalls, 2);
    expect(_button(tester, 'Voy'), isA<ElevatedButton>());
    expect(_button(tester, 'No voy'), isA<OutlinedButton>());
  });

  testWidgets('"No voy" y "Tal vez" envían DECLINED y MAYBE', (tester) async {
    final repository = FakeEventRepository(eventDetail: _event());
    await _pump(tester, repository);

    await _tapButton(tester, 'No voy');
    await _tapButton(tester, 'Tal vez');

    expect(
      repository.rsvpCalls.map((c) => c.$2),
      [RSVPStatus.declined, RSVPStatus.maybe],
    );
    expect(find.text('Tu respuesta: Tal vez'), findsOneWidget);
  });

  testWidgets('un ACCEPTED que vuelve WAITLISTED avisa la lista de espera',
      (tester) async {
    final repository = FakeEventRepository(
      eventDetail: _event(),
      rsvpResponse: RSVP(
        eventId: 'evt_1',
        userId: 'usr_2',
        status: RSVPStatus.waitlisted,
        respondedAt: DateTime.utc(2026, 11, 20, 15),
        waitlistedAt: DateTime.utc(2026, 11, 20, 15),
      ),
    );
    await _pump(tester, repository);

    await _tapButton(tester, 'Voy');

    expect(
      find.text('Estás en lista de espera: el evento no tiene cupo por ahora.'),
      findsOneWidget,
    );
    expect(find.text('Tu respuesta: Voy (en lista de espera)'), findsOneWidget);
    expect(_button(tester, 'Voy'), isA<ElevatedButton>());
  });

  testWidgets('un error del backend se muestra sin perder el detalle',
      (tester) async {
    final repository = FakeEventRepository(
      eventDetail: _event(),
      rsvpFailure: const ServerFailure(message: 'Ya respondiste a este evento'),
    );
    await _pump(tester, repository);

    await _tapButton(tester, 'Voy');

    expect(find.text('Ya respondiste a este evento'), findsOneWidget);
    expect(find.text('Los 30 de Marta'), findsOneWidget);
    expect(find.textContaining('Tu respuesta'), findsNothing);
    expect(_button(tester, 'Voy').onPressed, isNotNull);
  });

  testWidgets('en un evento cancelado los botones están deshabilitados',
      (tester) async {
    final repository =
        FakeEventRepository(eventDetail: _event(status: EventStatus.cancelled));
    await _pump(tester, repository);

    for (final label in const ['Voy', 'Tal vez', 'No voy']) {
      expect(_button(tester, label).onPressed, isNull, reason: label);
    }
    expect(
      find.text('El evento fue cancelado: ya no se puede responder.'),
      findsOneWidget,
    );
  });

  testWidgets('en un evento que ya empezó los botones están deshabilitados',
      (tester) async {
    final repository = FakeEventRepository(
      eventDetail:
          _event(startDate: DateTime.now().subtract(const Duration(hours: 2))),
    );
    await _pump(tester, repository);

    expect(_button(tester, 'Voy').onPressed, isNull);
    expect(
      find.text('El evento ya empezó o terminó: ya no se puede responder.'),
      findsOneWidget,
    );
  });

  group('EventBloc compartido con otras pantallas', () {
    Event named(String id, String title) => Event(
          id: id,
          organizerId: 'usr_1',
          title: title,
          status: EventStatus.published,
          details: EventDetails(
            startDate: DateTime.now().add(const Duration(days: 30)),
          ),
          stats: const EventStats(totalAccepted: 1),
        );

    Future<EventBloc> pumpShared(
      WidgetTester tester,
      _ByIdRepository repository,
      String eventId,
    ) async {
      tester.view.physicalSize = const Size(800, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final bloc = EventBloc(eventRepository: repository);
      addTearDown(bloc.close);
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: const [
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: const [Locale('es')],
          locale: const Locale('es'),
          home: BlocProvider<EventBloc>.value(
            value: bloc,
            child: EventDetailPage(eventId: eventId),
          ),
        ),
      );
      await tester.pumpAndSettle();
      return bloc;
    }

    testWidgets('si la carga de B falla, no muestra el evento A cargado antes',
        (tester) async {
      final repository = _ByIdRepository({'evt_A': named('evt_A', 'Evento A')});
      final bloc = EventBloc(eventRepository: repository);
      addTearDown(bloc.close);
      bloc.add(const LoadEvent('evt_A'));
      await bloc.stream.firstWhere((s) => s is EventLoaded);

      tester.view.physicalSize = const Size(800, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MaterialApp(
          home: BlocProvider<EventBloc>.value(
            value: bloc,
            child: const EventDetailPage(eventId: 'evt_B'),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Evento A'), findsNothing);
      expect(find.text('Voy'), findsNothing);
      expect(find.text('Reintentar'), findsOneWidget);
    });

    testWidgets('un EventLoaded de otro evento no cambia el detalle ni el RSVP',
        (tester) async {
      final repository = _ByIdRepository({
        'evt_A': named('evt_A', 'Evento A'),
        'evt_B': named('evt_B', 'Evento B'),
      });
      final bloc = await pumpShared(tester, repository, 'evt_A');

      // Otra pantalla carga B con el mismo bloc.
      bloc.add(const LoadEvent('evt_B'));
      await tester.pumpAndSettle();

      expect(find.text('Evento A'), findsOneWidget);
      expect(find.text('Evento B'), findsNothing);

      await _tapButton(tester, 'Voy');
      expect(repository.rsvpCalls.single.$1, 'evt_A');
    });

    testWidgets('un error de otra pantalla no se muestra en el detalle',
        (tester) async {
      final repository = _ByIdRepository({'evt_A': named('evt_A', 'Evento A')});
      final bloc = await pumpShared(tester, repository, 'evt_A');

      // P. ej. el escáner: su check-in falla con el mismo bloc.
      bloc.add(const CheckIn(eventId: 'evt_A', qrCode: 'qr'));
      await tester.pumpAndSettle();

      expect(find.byType(SnackBar), findsNothing);
      expect(find.text('Evento A'), findsOneWidget);
      expect(_button(tester, 'Voy').onPressed, isNotNull);
    });
  });
}

/// Repositorio con un detalle por id; un id desconocido responde
/// `ServerFailure`.
class _ByIdRepository extends FakeEventRepository {
  final Map<String, Event> events;

  _ByIdRepository(this.events);

  @override
  Future<Either<Failure, Event>> getEvent(String eventId) async {
    final event = events[eventId];
    return event != null ? Right(event) : const Left(ServerFailure());
  }
}
