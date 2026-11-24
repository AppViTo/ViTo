import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/features/events/data/models/event_patch.dart';
import 'package:vito_app/features/events/domain/entities/event.dart';
import 'package:vito_app/features/events/presentation/bloc/edit_event_cubit.dart';
import 'package:vito_app/features/events/presentation/pages/edit_event_page.dart';

import 'fakes.dart';

Event _event() => Event(
      id: 'evt_1',
      organizerId: 'usr_1',
      title: 'Los 30 de Marta',
      description: 'Celebramos juntos',
      status: EventStatus.published,
      details: EventDetails(
        startDate: DateTime(2026, 12, 26, 20),
        endDate: DateTime(2026, 12, 27, 2),
        location: 'Medellín',
        address: 'Cra. 7 #63-14',
        category: 'BIRTHDAY',
      ),
      settings: const EventSettings(
        isPublic: false,
        maxAttendees: 40,
        hideAddressUntilAccepted: true,
      ),
      stats: const EventStats(totalAccepted: 12),
    );

/// Resultado con el que se cerró la página (el evento guardado o `null`).
Object? _popped;
bool _closed = false;

Future<void> _pump(
  WidgetTester tester,
  FakeEventRepository repository, {
  Event? event,
}) async {
  tester.view.physicalSize = const Size(800, 2400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  _popped = null;
  _closed = false;
  await tester.pumpWidget(
    MaterialApp(
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [Locale('es')],
      locale: const Locale('es'),
      home: Builder(
        builder: (context) => Scaffold(
          body: TextButton(
            onPressed: () async {
              _popped = await Navigator.of(context).push(MaterialPageRoute(
                builder: (_) => BlocProvider(
                  create: (_) => EditEventCubit(repository: repository),
                  child: EditEventPage(event: event ?? _event()),
                ),
              ));
              _closed = true;
            },
            child: const Text('abrir'),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('abrir'));
  await tester.pumpAndSettle();
}

Future<void> _save(WidgetTester tester) async {
  await tester.tap(find.text('Guardar cambios'));
  await tester.pumpAndSettle();
}

/// T049 (Bug-F07): el organizador edita su evento; se envía solo lo que
/// cambió y los errores 400/409 muestran el mensaje del backend.
void main() {
  setUpAll(() async {
    await initializeDateFormatting('es');
  });

  testWidgets('carga los datos actuales del evento', (tester) async {
    await _pump(tester, FakeEventRepository());

    expect(find.text('Los 30 de Marta'), findsOneWidget);
    expect(find.text('Celebramos juntos'), findsOneWidget);
    expect(find.text('Medellín'), findsOneWidget);
    expect(find.text('Cra. 7 #63-14'), findsOneWidget);
    expect(find.text('40'), findsOneWidget);
    expect(find.textContaining('26 de diciembre de 2026'), findsOneWidget);
  });

  testWidgets('guarda solo el nombre y el cupo cambiados y cierra', (tester) async {
    final repository = FakeEventRepository();
    await _pump(tester, repository);

    await tester.enterText(
        find.byKey(const ValueKey('edit-title')), 'Los 30 de Marta 🎉');
    await tester.enterText(find.byKey(const ValueKey('edit-capacity')), '60');
    await _save(tester);

    final (original, edited) = repository.updateCalls.single;
    expect(eventPatchJson(original, edited), {
      'title': 'Los 30 de Marta 🎉',
      'settings': {'maxAttendees': 60},
    });
    expect(_closed, isTrue);
    expect((_popped as Event).title, 'Los 30 de Marta 🎉');
  });

  testWidgets('vaciar la dirección y apagar "Ocultar la dirección"',
      (tester) async {
    final repository = FakeEventRepository();
    await _pump(tester, repository);

    await tester.enterText(find.byKey(const ValueKey('edit-address')), '');
    await tester.tap(find.text('Ocultar la dirección'));
    await tester.pumpAndSettle();
    await _save(tester);

    final (original, edited) = repository.updateCalls.single;
    expect(eventPatchJson(original, edited), {
      'details': {'address': ''},
      'settings': {'hideAddressUntilAccepted': false},
    });
  });

  testWidgets('un campo que no tenía valor y sigue vacío no se envía',
      (tester) async {
    final repository = FakeEventRepository();
    final base = _event();
    await _pump(
      tester,
      repository,
      event: Event(
        id: base.id,
        organizerId: base.organizerId,
        title: base.title,
        description: base.description,
        status: base.status,
        details: EventDetails(startDate: base.details!.startDate),
        settings: base.settings,
      ),
    );

    await tester.enterText(find.byKey(const ValueKey('edit-capacity')), '60');
    await _save(tester);

    final (original, edited) = repository.updateCalls.single;
    expect(eventPatchJson(original, edited), {
      'settings': {'maxAttendees': 60},
    });
  });

  testWidgets('guardar sin tocar nada, con espacios sobrantes, no envía nada',
      (tester) async {
    final repository = FakeEventRepository();
    final base = _event();
    await _pump(
      tester,
      repository,
      event: Event(
        id: base.id,
        organizerId: base.organizerId,
        title: '  Los 30 de Marta ',
        description: 'Celebramos juntos  ',
        status: base.status,
        details: EventDetails(
          startDate: base.details!.startDate,
          endDate: base.details!.endDate,
          location: ' Medellín',
          address: 'Cra. 7 #63-14   ',
          category: 'BIRTHDAY',
        ),
        settings: base.settings,
      ),
    );

    await _save(tester);

    final (original, edited) = repository.updateCalls.single;
    expect(eventPatchJson(original, edited), isEmpty);
  });

  testWidgets('la nueva portada reemplaza solo la primera imagen',
      (tester) async {
    final repository = FakeEventRepository();
    final base = _event();
    await _pump(
      tester,
      repository,
      event: Event(
        id: base.id,
        organizerId: base.organizerId,
        title: base.title,
        description: base.description,
        status: base.status,
        details: EventDetails(
          startDate: base.details!.startDate,
          endDate: base.details!.endDate,
          location: base.details!.location,
          address: base.details!.address,
          coverImages: const [
            'https://cdn.vito.app/a.jpg',
            'https://cdn.vito.app/b.jpg',
            'https://cdn.vito.app/c.jpg',
          ],
          category: 'BIRTHDAY',
        ),
        settings: base.settings,
      ),
    );

    final cubit = BlocProvider.of<EditEventCubit>(
        tester.element(find.byType(EditEventPage)));
    await cubit.uploadCover(
      eventId: 'evt_1',
      bytes: Uint8List.fromList([1, 2, 3]),
      fileName: 'cover.jpg',
      mimeType: 'image/jpeg',
    );
    await tester.pumpAndSettle();
    await _save(tester);

    final (original, edited) = repository.updateCalls.single;
    expect(eventPatchJson(original, edited), {
      'details': {
        'coverImages': [
          'https://cdn.vito.app/cover.jpg',
          'https://cdn.vito.app/b.jpg',
          'https://cdn.vito.app/c.jpg',
        ],
      },
    });
  });

  testWidgets('"Empieza" abre el calendario en un evento que ya empezó',
      (tester) async {
    final base = _event();
    final started = DateTime.now().subtract(const Duration(days: 3));
    await _pump(
      tester,
      FakeEventRepository(),
      event: Event(
        id: base.id,
        organizerId: base.organizerId,
        title: base.title,
        status: base.status,
        details: EventDetails(
          startDate: started,
          endDate: started.add(const Duration(days: 5)),
        ),
        settings: base.settings,
      ),
    );

    await tester.tap(find.text('Empieza'));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.byType(DatePickerDialog), findsOneWidget);
  });

  testWidgets('un 409 muestra el mensaje del backend y no cierra',
      (tester) async {
    final repository = FakeEventRepository(
      updateFailure: const ConflictFailure(
          message: 'El cupo no puede ser menor que los 12 confirmados'),
    );
    await _pump(tester, repository);

    await tester.enterText(find.byKey(const ValueKey('edit-capacity')), '5');
    await _save(tester);

    expect(find.text('El cupo no puede ser menor que los 12 confirmados'),
        findsOneWidget);
    expect(_closed, isFalse);
    expect(find.text('Editar evento'), findsOneWidget);
  });

  group('clampDate', () {
    final first = DateTime(2026, 11, 23);
    final last = DateTime(2028, 11, 23);

    test('deja igual una fecha dentro del rango', () {
      final date = DateTime(2027, 5, 1, 20);
      expect(clampDate(date, first, last), date);
    });

    test('una fecha anterior queda en el primer día', () {
      expect(clampDate(DateTime(2026, 1, 1), first, last), first);
    });

    test('una fecha posterior queda en el último día', () {
      expect(clampDate(DateTime(2030, 1, 1), first, last), last);
    });
  });

  testWidgets('un nombre de menos de 3 caracteres no se envía', (tester) async {
    final repository = FakeEventRepository();
    await _pump(tester, repository);

    await tester.enterText(find.byKey(const ValueKey('edit-title')), 'Ab');
    await _save(tester);

    expect(find.text('Mínimo 3 caracteres'), findsOneWidget);
    expect(repository.updateCalls, isEmpty);
  });
}
