import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'package:vito_app/features/events/domain/entities/event.dart';
import 'package:vito_app/features/events/presentation/bloc/event_bloc.dart';
import 'package:vito_app/features/events/presentation/pages/event_detail_page.dart';

import 'fakes.dart';

Event _event() => Event(
      id: 'evt_1',
      organizerId: 'usr_1',
      title: 'Los 30 de Marta',
      description: 'Celebramos juntos',
      status: EventStatus.published,
      settings: const EventSettings(isPublic: false),
      details: EventDetails(
        startDate: DateTime(2026, 12, 26, 20, 0),
        location: 'Medellín',
        address: 'Cra. 7 #63-14',
        category: 'BIRTHDAY',
      ),
      stats: const EventStats(
        totalInvited: 10,
        totalAccepted: 6,
        totalDeclined: 1,
        totalMaybe: 2,
      ),
    );

/// Bug-F10: el detalle del evento debe estar en español y con fechas en `es`.
void main() {
  setUpAll(() async {
    await initializeDateFormatting('es');
  });

  testWidgets('traduce las etiquetas, los estados y la fecha', (tester) async {
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
          create: (_) => EventBloc(
            eventRepository: FakeEventRepository(eventDetail: _event()),
          ),
          child: const EventDetailPage(eventId: 'evt_1'),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Descripción'), findsOneWidget);
    expect(find.text('Fecha y hora'), findsOneWidget);
    expect(find.text('Ubicación'), findsOneWidget);
    expect(find.text('Dirección'), findsOneWidget);
    expect(find.text('Categoría'), findsOneWidget);
    expect(find.text('Estadísticas'), findsOneWidget);
    expect(find.text('Publicado'), findsOneWidget);
    expect(find.text('Privado'), findsOneWidget);
    expect(find.text('Invitados'), findsOneWidget);
    expect(find.text('Confirmados'), findsOneWidget);
    expect(find.text('Rechazados'), findsOneWidget);
    expect(find.text('Tal vez'), findsOneWidget);
    expect(find.text('Confirmar asistencia'), findsOneWidget);
    expect(find.text('Registrar entrada'), findsOneWidget);
    expect(find.text('Mosaico'), findsOneWidget);
    expect(find.text('Iniciar transmisión'), findsOneWidget);

    expect(
      find.textContaining('sábado, 26 de diciembre de 2026'),
      findsOneWidget,
    );
    expect(find.textContaining('Saturday'), findsNothing);
    expect(find.textContaining('December'), findsNothing);

    for (final english in const [
      'About',
      'Date & Time',
      'Location',
      'Address',
      'Category',
      'Statistics',
      'Draft',
      'Published',
      'Private',
    ]) {
      expect(find.text(english), findsNothing);
    }
  });
}
