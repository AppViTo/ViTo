import 'package:bloc_test/bloc_test.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'package:vito_app/features/auth/domain/entities/user.dart';
import 'package:vito_app/features/auth/presentation/bloc/auth_bloc.dart';
import 'package:vito_app/features/events/domain/entities/event.dart';
import 'package:vito_app/features/home/presentation/pages/home_screen.dart';
import 'package:vito_app/features/home/presentation/pages/people_screen.dart';

import '../events/fakes.dart';

class MockAuthBloc extends MockBloc<AuthEvent, AuthState> implements AuthBloc {}

const _user = User(
  id: 'usr_42',
  username: 'bernardo',
  email: 'bernardo@example.com',
  profile: UserProfile(displayName: 'Bernardo'),
);

Event _event(String id, String title, {DateTime? start, EventStatus? status}) =>
    Event(
      id: id,
      organizerId: 'usr_42',
      title: title,
      status: status ?? EventStatus.published,
      details: EventDetails(
        startDate: start ?? DateTime.now().add(const Duration(days: 3)),
        location: 'Medellín',
      ),
    );

/// Bug-F02, F04 y F06: el dashboard y "Mi Gente" muestran solo datos reales
/// (sesión y `listEvents`), y estados vacíos donde aún no hay contrato.
void main() {
  late MockAuthBloc authBloc;

  setUpAll(() async {
    await initializeDateFormatting('es');
  });

  setUp(() {
    authBloc = MockAuthBloc();
    whenListen(
      authBloc,
      const Stream<AuthState>.empty(),
      initialState: const AuthAuthenticated(user: _user),
    );
  });

  Future<void> pumpHome(WidgetTester tester, FakeEventRepository repo) async {
    await tester.pumpWidget(
      BlocProvider<AuthBloc>.value(
        value: authBloc,
        child: MaterialApp(
          home: Scaffold(body: HomeScreen(eventRepository: repo)),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('saluda con el nombre del usuario de la sesión', (tester) async {
    await pumpHome(tester, FakeEventRepository(myEvents: [], upcomingEvents: []));

    expect(find.text('Bernardo'), findsOneWidget);
    expect(find.text('Alex'), findsNothing);
  });

  testWidgets('sin datos muestra los estados vacíos', (tester) async {
    await pumpHome(tester, FakeEventRepository(myEvents: [], upcomingEvents: []));

    expect(find.text('Aún no tienes eventos para asistir'), findsOneWidget);
    await tester.scrollUntilVisible(
      find.text('Aún no tienes amigos'),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('No hay transmisiones en vivo de tus eventos'), findsOneWidget);
    expect(find.text('Aún no hay historias por mostrar'), findsOneWidget);
    expect(find.text('Aún no tienes amigos'), findsOneWidget);
  });

  testWidgets('muestra los eventos propios y los próximos sin duplicar',
      (tester) async {
    final cumple = _event('evt_1', 'Cumpleaños de Ana');
    await pumpHome(
      tester,
      FakeEventRepository(
        myEvents: [
          cumple,
          _event('evt_old', 'Evento pasado',
              start: DateTime.now().subtract(const Duration(days: 2))),
          _event('evt_x', 'Evento cancelado', status: EventStatus.cancelled),
        ],
        upcomingEvents: [cumple, _event('evt_2', 'Concierto')],
      ),
    );

    expect(find.text('Cumpleaños de Ana'), findsOneWidget);
    expect(find.text('Concierto'), findsOneWidget);
    expect(find.text('Evento pasado'), findsNothing);
    expect(find.text('Evento cancelado'), findsNothing);
  });

  testWidgets('el buscador filtra sobre los eventos reales', (tester) async {
    await pumpHome(
      tester,
      FakeEventRepository(
        myEvents: [_event('evt_1', 'Cumpleaños de Ana')],
        upcomingEvents: [_event('evt_2', 'Concierto')],
      ),
    );

    await tester.enterText(find.byType(TextField).first, 'cumple');
    await tester.pumpAndSettle();

    expect(find.text('Cumpleaños de Ana'), findsOneWidget);
    expect(find.text('Concierto'), findsNothing);
  });

  testWidgets('si fallan ambas consultas lo dice en vez de mostrar vacío',
      (tester) async {
    await pumpHome(tester, FakeEventRepository());

    expect(find.textContaining('No pudimos cargar tus eventos'), findsOneWidget);
  });

  testWidgets('Mi Gente no muestra amigos de ejemplo', (tester) async {
    await tester.pumpWidget(
      BlocProvider<AuthBloc>.value(
        value: authBloc,
        child: const MaterialApp(home: PeopleScreen()),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Aún no tienes amigos'), findsOneWidget);
    expect(find.byIcon(Icons.message_rounded), findsNothing);

    await tester.tap(find.text('Seguidos'));
    await tester.pumpAndSettle();
    expect(find.text('Aún no sigues a nadie'), findsOneWidget);
  });
}
