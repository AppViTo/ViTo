import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/features/auth/presentation/bloc/auth_bloc.dart';
import 'package:vito_app/features/home/presentation/pages/people_screen.dart';
import 'package:vito_app/features/social/domain/entities/social_profile.dart';

import '../events/auth_stub.dart';
import 'fakes.dart';

Future<void> _pump(WidgetTester tester, FakeGraphRepository repository) async {
  tester.view.physicalSize = const Size(800, 1600);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    BlocProvider<AuthBloc>(
      create: (_) => StubAuthBloc('usr_1'),
      child: MaterialApp(home: PeopleScreen(repository: repository)),
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _search(WidgetTester tester, String text) async {
  await tester.enterText(find.byType(TextField), text);
  await tester.pump(const Duration(milliseconds: 350));
  await tester.pumpAndSettle();
}

/// T054: "Mi Gente" con a quién sigues, quién te sigue y la búsqueda, sin
/// datos de ejemplo.
void main() {
  testWidgets('muestra a quién sigue la persona con sesión', (tester) async {
    final repository = FakeGraphRepository(followingPages: [
      [person(2), person(3, withName: false)],
    ]);
    await _pump(tester, repository);

    expect(repository.calls.first, 'following usr_1 0');
    expect(find.text('Persona 2'), findsOneWidget);
    expect(find.text('@persona2'), findsOneWidget);
    // Sin nombre visible se muestra el usuario.
    expect(find.text('persona3'), findsOneWidget);
  });

  testWidgets('la pestaña "Seguidores" lista a quién te sigue', (tester) async {
    final repository = FakeGraphRepository(followerPages: [
      [person(4)],
    ]);
    await _pump(tester, repository);

    await tester.tap(find.text('Seguidores'));
    await tester.pumpAndSettle();

    expect(repository.calls, contains('followers usr_1 0'));
    expect(find.text('Persona 4'), findsOneWidget);
  });

  testWidgets('sin datos muestra los estados vacíos y nada de ejemplo',
      (tester) async {
    await _pump(tester, FakeGraphRepository());

    expect(find.text('Aún no sigues a nadie'), findsOneWidget);
    expect(find.text('Sugerencias'), findsNothing);
    expect(find.byIcon(Icons.message_rounded), findsNothing);

    await tester.tap(find.text('Seguidores'));
    await tester.pumpAndSettle();
    expect(find.text('Aún nadie te sigue'), findsOneWidget);
  });

  testWidgets('busca desde 2 caracteres y vuelve a las listas al borrar',
      (tester) async {
    final repository = FakeGraphRepository(
      followingPages: [
        [person(2)],
      ],
      searchResults: [person(7)],
    );
    await _pump(tester, repository);

    await _search(tester, 'p');
    expect(repository.calls.where((c) => c.startsWith('search')), isEmpty);

    await _search(tester, '@pe');
    expect(repository.calls.last, 'search pe');
    expect(find.text('Persona 7'), findsOneWidget);
    expect(find.text('Siguiendo'), findsNothing);

    await _search(tester, '');
    expect(find.text('Persona 2'), findsOneWidget);
    expect(find.text('Siguiendo'), findsOneWidget);
  });

  testWidgets('una búsqueda sin resultados lo dice', (tester) async {
    await _pump(tester, FakeGraphRepository());

    await _search(tester, 'zz');

    expect(find.text('No encontramos a nadie con ese nombre o usuario'),
        findsOneWidget);
  });

  testWidgets('tocar a alguien abre su perfil', (tester) async {
    final repository = FakeGraphRepository(
      followingPages: [
        [person(2)],
      ],
      profiles: {
        'usr_2': const SocialProfile(
          userId: 'usr_2',
          username: 'persona2',
          displayName: 'Persona 2',
          isFollowing: true,
        ),
      },
    );
    await _pump(tester, repository);

    await tester.tap(find.text('Persona 2'));
    await tester.pumpAndSettle();

    expect(repository.calls.last, 'profile usr_2');
    expect(find.text('@persona2'), findsOneWidget);
    expect(find.widgetWithText(OutlinedButton, 'Siguiendo'), findsOneWidget);
  });
}
