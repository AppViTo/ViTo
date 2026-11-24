import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/features/auth/presentation/bloc/auth_bloc.dart';
import 'package:vito_app/features/home/presentation/pages/people_screen.dart';
import 'package:vito_app/features/social/domain/entities/social_profile.dart';

import '../events/auth_stub.dart';
import 'fakes.dart';

Future<void> _pump(
  WidgetTester tester,
  FakeGraphRepository repository, {
  Listenable? reloadSignal,
}) async {
  tester.view.physicalSize = const Size(800, 1600);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    BlocProvider<AuthBloc>(
      create: (_) => StubAuthBloc('usr_1'),
      child: MaterialApp(
        home: PeopleScreen(repository: repository, reloadSignal: reloadSignal),
      ),
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

  testWidgets('una primera página corta sigue con la siguiente sin scroll',
      (tester) async {
    final repository = FakeGraphRepository(followingPages: [
      [person(2)],
      [person(3)],
    ]);
    await _pump(tester, repository);

    expect(find.text('Persona 3'), findsOneWidget);
    expect(repository.calls, [
      'following usr_1 0',
      'following usr_1 1',
      'following usr_1 2',
    ]);
  });

  testWidgets('tras un error, el scroll no reintenta; lo hace el botón',
      (tester) async {
    final repository = FakeGraphRepository(followingPages: [
      [for (var i = 1; i <= 30; i++) person(i)],
      [person(31)],
    ])
      ..failingPages.add(1);
    await _pump(tester, repository);

    final list = find.descendant(
      of: find.byType(ListView),
      matching: find.byType(Scrollable),
    );
    await tester.scrollUntilVisible(
      find.textContaining('Reintentar'),
      400,
      scrollable: list,
    );
    await tester.pumpAndSettle();
    expect(repository.calls, ['following usr_1 0', 'following usr_1 1']);

    // Más scroll con el error a la vista no pide otra vez.
    await tester.drag(list, const Offset(0, 300));
    await tester.pumpAndSettle();
    await tester.drag(list, const Offset(0, -600));
    await tester.pumpAndSettle();
    expect(repository.calls, ['following usr_1 0', 'following usr_1 1']);

    repository.failingPages.clear();
    await tester.tap(find.textContaining('Reintentar'));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.text('Persona 31'), 400,
        scrollable: list);
    await tester.pumpAndSettle();
    expect(find.text('Persona 31'), findsOneWidget);
    expect(repository.calls.sublist(2), [
      'following usr_1 1',
      'following usr_1 2',
    ]);
  });

  testWidgets('al volver de un perfil recarga las listas', (tester) async {
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
    final before =
        repository.calls.where((c) => c == 'following usr_1 0').length;

    await tester.tap(find.text('Persona 2'));
    await tester.pumpAndSettle();
    tester.state<NavigatorState>(find.byType(Navigator)).pop();
    await tester.pumpAndSettle();

    expect(repository.calls.where((c) => c == 'following usr_1 0').length,
        before + 1);
  });

  testWidgets('el aviso de la pestaña recarga las listas', (tester) async {
    final signal = ValueNotifier<int>(0);
    addTearDown(signal.dispose);
    final repository = FakeGraphRepository(followingPages: [
      [person(2)],
    ]);
    await _pump(tester, repository, reloadSignal: signal);
    final before =
        repository.calls.where((c) => c == 'following usr_1 0').length;

    signal.value++;
    await tester.pumpAndSettle();

    expect(repository.calls.where((c) => c == 'following usr_1 0').length,
        before + 1);
  });
}
