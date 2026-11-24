import 'package:dartz/dartz.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/features/events/domain/repositories/event_repository.dart';
import 'package:vito_app/features/events/presentation/bloc/event_bloc.dart';
import 'package:vito_app/features/events/presentation/pages/create_event_screen.dart';
import 'package:vito_app/features/social/data/datasources/user_remote_datasource.dart';
import 'package:vito_app/features/social/domain/entities/social_profile.dart';
import 'package:vito_app/features/social/domain/entities/user_summary.dart';
import 'package:vito_app/features/social/domain/repositories/social_repository.dart';
import 'package:vito_app/features/social/domain/usecases/follow.dart';
import 'package:vito_app/features/social/domain/usecases/get_profile.dart';
import 'package:vito_app/features/social/domain/usecases/unfollow.dart';
import 'package:vito_app/features/social/presentation/bloc/social_bloc.dart';

import 'fakes.dart';

class _FakeSocialRepository implements SocialRepository {
  final List<UserSummary> results;

  /// Búsquedas recibidas, ya normalizadas por el caso de uso.
  final List<String> queries = [];
  final Failure? searchFailure;

  /// Resultados y demora por búsqueda (normalizada); si no hay, [results]
  /// sin demora.
  final Map<String, List<UserSummary>> resultsByQuery;
  final Map<String, Duration> delayByQuery;

  _FakeSocialRepository({
    this.results = const [],
    this.searchFailure,
    this.resultsByQuery = const {},
    this.delayByQuery = const {},
  });

  @override
  Future<Either<Failure, SocialProfile>> getProfile(String userId) async =>
      const Left(ServerFailure());

  @override
  Future<Either<Failure, List<UserSummary>>> searchUsers({
    required String query,
    int page = 0,
    int size = 20,
  }) async {
    queries.add(query);
    final delay = delayByQuery[query];
    if (delay != null) await Future<void>.delayed(delay);
    final failure = searchFailure;
    return failure != null
        ? Left(failure)
        : Right(resultsByQuery[query] ?? results);
  }

  @override
  Future<Either<Failure, void>> follow(String userId) async =>
      const Left(ServerFailure());

  @override
  Future<Either<Failure, void>> unfollow(String userId) async =>
      const Left(ServerFailure());

  @override
  Future<Either<Failure, bool>> isFollowing(String userId) async =>
      const Right(false);

  @override
  Future<Either<Failure, List<SocialProfile>>> getFollowers({
    required String userId,
    int page = 0,
    int size = 20,
  }) async =>
      const Right([]);

  @override
  Future<Either<Failure, List<SocialProfile>>> getFollowing({
    required String userId,
    int page = 0,
    int size = 20,
  }) async =>
      const Right([]);
}

Future<void> _pumpWizard(
  WidgetTester tester, {
  required EventRepository eventRepository,
  SocialRepository? socialRepository,
  bool withRoutes = false,
}) async {
  final social = socialRepository ?? _FakeSocialRepository();
  await tester.pumpWidget(
    MaterialApp(
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [Locale('es'), Locale('en')],
      locale: const Locale('es'),
      home: MultiBlocProvider(
        providers: [
          BlocProvider<EventBloc>(
            create: (_) => EventBloc(eventRepository: eventRepository),
          ),
          BlocProvider<SocialBloc>(
            create: (_) => SocialBloc(
              followUseCase: FollowUseCase(social),
              unfollowUseCase: UnfollowUseCase(social),
              getProfileUseCase: GetProfileUseCase(social),
              socialRepository: social,
            ),
          ),
        ],
        child: const CreateEventScreen(),
      ),
      onGenerateRoute: withRoutes
          ? (settings) => MaterialPageRoute(
                builder: (_) => Scaffold(body: Text('ruta ${settings.name}')),
              )
          : null,
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _goToStep2(WidgetTester tester) async {
  await tester.tap(find.text('Continuar'));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Continuar'));
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(() async {
    await initializeDateFormatting('es');
  });

  group('CreateEventScreen — Paso 1 "Nuevo evento"', () {
    testWidgets('muestra el título, los 3 segmentos y el nombre prellenado',
        (tester) async {
      await _pumpWizard(
        tester,
        eventRepository: FakeEventRepository(),
      );

      expect(find.text('Nuevo evento'), findsOneWidget);
      expect(find.text('Los 30 de Marta'), findsOneWidget);
      expect(find.text('Cumpleaños'), findsOneWidget);
      expect(find.text('Continuar'), findsOneWidget);

      final header = tester.widget<WizardHeader>(find.byType(WizardHeader));
      expect(header.stepCount, 3);
      expect(header.currentStep, 0);
      expect(header.title, 'Nuevo evento');
    });

    testWidgets('el chip de tipo por defecto está marcado como seleccionado',
        (tester) async {
      await _pumpWizard(
        tester,
        eventRepository: FakeEventRepository(),
      );

      final cumpleanos = tester.widget<OptionChip>(
        find.widgetWithText(OptionChip, 'Cumpleaños'),
      );
      final boda = tester.widget<OptionChip>(
        find.widgetWithText(OptionChip, 'Boda'),
      );

      expect(cumpleanos.selected, isTrue);
      expect(boda.selected, isFalse);
    });

    testWidgets('valida que el nombre no esté vacío', (tester) async {
      await _pumpWizard(
        tester,
        eventRepository: FakeEventRepository(),
      );

      await tester.enterText(find.byType(TextFormField).first, '');
      await tester.tap(find.text('Continuar'));
      await tester.pumpAndSettle();

      expect(find.text('Escribe el nombre del evento'), findsOneWidget);
      expect(find.text('Nuevo evento'), findsOneWidget);
    });

    testWidgets('el mensaje admite como máximo 2000 caracteres',
        (tester) async {
      await _pumpWizard(
        tester,
        eventRepository: FakeEventRepository(),
      );

      final fields = find.byType(WizardField);
      expect(tester.widget<WizardField>(fields.first).label,
          'NOMBRE DEL EVENTO');
      expect(tester.widget<WizardField>(fields.last).label,
          'MENSAJE PARA TUS INVITADOS');
      expect(tester.widget<WizardField>(fields.last).maxLength, 2000);
    });
  });

  group('CreateEventScreen — navegación entre pasos', () {
    testWidgets('avanza al paso 2 "Cuándo y dónde"', (tester) async {
      await _pumpWizard(
        tester,
        eventRepository: FakeEventRepository(),
      );

      await tester.tap(find.text('Continuar'));
      await tester.pumpAndSettle();

      expect(find.text('Cuándo y dónde'), findsOneWidget);
      expect(find.text('Cambiar'), findsOneWidget);
      expect(find.text('DIRECCIÓN'), findsOneWidget);
      expect(find.text('Mostrar solo el barrio hasta que acepten'),
          findsOneWidget);
    });

    testWidgets('retrocede al paso anterior con el botón "<"',
        (tester) async {
      await _pumpWizard(
        tester,
        eventRepository: FakeEventRepository(),
      );

      await _goToStep2(tester);
      expect(find.text('¿A quién invitamos?'), findsOneWidget);

      await tester.tap(find.byIcon(Icons.arrow_back_ios_new));
      await tester.pumpAndSettle();
      expect(find.text('Cuándo y dónde'), findsOneWidget);

      await tester.tap(find.byIcon(Icons.arrow_back_ios_new));
      await tester.pumpAndSettle();
      expect(find.text('Nuevo evento'), findsOneWidget);
    });

    testWidgets('la tarjeta de fecha muestra el rango en español',
        (tester) async {
      await _pumpWizard(
        tester,
        eventRepository: FakeEventRepository(),
      );

      await tester.tap(find.text('Continuar'));
      await tester.pumpAndSettle();

      final rangeFinder = find.textContaining('—');
      expect(rangeFinder, findsOneWidget);

      final value = tester.widget<Text>(rangeFinder).data ?? '';

      expect(value, contains('de'));
      expect(value.toLowerCase(), contains('p. m.'));
      expect(value.contains('Saturday'), isFalse);
      expect(value.contains('August'), isFalse);
      expect(value, matches(RegExp(r'^[A-ZÁÉÍÓÚ][a-záéíóú]+,')));
    });
  });

  group('CreateEventScreen — Paso 3 "¿A quién invitamos?"', () {
    testWidgets('muestra el buscador y oculta el enlace hasta que exista',
        (tester) async {
      await _pumpWizard(
        tester,
        eventRepository: FakeEventRepository(),
      );
      await _goToStep2(tester);

      expect(find.byType(TextField), findsOneWidget);
      expect(find.text('COMPARTE EL ENLACE'), findsNothing);
      expect(find.text('Copiar enlace de invitación'), findsNothing);
      expect(find.text('Enviar por WhatsApp'), findsNothing);
      expect(find.text('Permitir que traigan acompañante'), findsOneWidget);
      expect(find.text('Crear evento'), findsOneWidget);
    });

    testWidgets('busca, añade un invitado y actualiza el CTA', (tester) async {
      await _pumpWizard(
        tester,
        eventRepository: FakeEventRepository(),
        socialRepository: _FakeSocialRepository(results: const [
          UserSummary(
            userId: 'usr_1',
            username: 'beto',
            displayName: 'Beto Ramírez',
          ),
          UserSummary(
            userId: 'usr_2',
            username: 'laura',
            displayName: 'Laura S.',
          ),
        ]),
      );
      await _goToStep2(tester);

      await tester.enterText(find.byType(TextField), 'beto');
      await tester.pump(const Duration(milliseconds: 400));
      await tester.pumpAndSettle();

      expect(find.text('Beto Ramírez'), findsOneWidget);
      expect(find.text('Laura S.'), findsOneWidget);

      await tester.tap(find.text('Beto Ramírez'));
      await tester.pumpAndSettle();

      expect(find.text('Enviar 1 invitación'), findsOneWidget);
      expect(find.text('+0 más'), findsNothing);
    });

    testWidgets('muestra "+N más" cuando hay más de 3 invitados',
        (tester) async {
      await _pumpWizard(
        tester,
        eventRepository: FakeEventRepository(),
        socialRepository: _FakeSocialRepository(results: const [
          UserSummary(userId: 'u1', username: 'a', displayName: 'Ana'),
          UserSummary(userId: 'u2', username: 'b', displayName: 'Beto'),
          UserSummary(userId: 'u3', username: 'c', displayName: 'Caro'),
          UserSummary(userId: 'u4', username: 'd', displayName: 'Diego'),
          UserSummary(userId: 'u5', username: 'e', displayName: 'Eva'),
        ]),
      );
      await _goToStep2(tester);

      await tester.enterText(find.byType(TextField), 'xy');
      await tester.pump(const Duration(milliseconds: 400));
      await tester.pumpAndSettle();

      for (final name
          in const ['Ana', 'Beto', 'Caro', 'Diego', 'Eva']) {
        await tester.tap(find.text(name));
        await tester.pumpAndSettle();
      }

      expect(find.text('+2 más'), findsOneWidget);
      expect(find.text('Enviar 5 invitaciones'), findsOneWidget);
    });

    testWidgets('sin resultados muestra el estado vacío', (tester) async {
      await _pumpWizard(
        tester,
        eventRepository: FakeEventRepository(),
        socialRepository: _FakeSocialRepository(),
      );
      await _goToStep2(tester);

      await tester.enterText(find.byType(TextField), 'zzz');
      await tester.pump(const Duration(milliseconds: 400));
      await tester.pumpAndSettle();

      expect(
        find.textContaining('No encontramos a nadie con ese nombre o usuario'),
        findsOneWidget,
      );
      expect(
        find.textContaining('La búsqueda de usuarios aún no está disponible'),
        findsNothing,
      );
    });

    testWidgets('no busca con menos de 2 caracteres y quita la @ inicial',
        (tester) async {
      final social = _FakeSocialRepository();
      await _pumpWizard(
        tester,
        eventRepository: FakeEventRepository(),
        socialRepository: social,
      );
      await _goToStep2(tester);

      await tester.enterText(find.byType(TextField), 'c');
      await tester.pump(const Duration(milliseconds: 400));
      await tester.enterText(find.byType(TextField), '@c');
      await tester.pump(const Duration(milliseconds: 400));
      expect(social.queries, isEmpty);

      await tester.enterText(find.byType(TextField), '@caro.mz');
      await tester.pump(const Duration(milliseconds: 400));
      await tester.pumpAndSettle();
      expect(social.queries, ['caro.mz']);
    });

    testWidgets('al borrar hasta 1 carácter no vuelven resultados viejos',
        (tester) async {
      final social = _FakeSocialRepository(
        resultsByQuery: const {
          'caro': [UserSummary(userId: 'usr_7', username: 'caro', displayName: 'Carolina')],
        },
        delayByQuery: const {'caro': Duration(seconds: 1)},
      );
      await _pumpWizard(
        tester,
        eventRepository: FakeEventRepository(),
        socialRepository: social,
      );
      await _goToStep2(tester);

      await tester.enterText(find.byType(TextField), 'caro');
      await tester.pump(const Duration(milliseconds: 400));
      expect(social.queries, ['caro']);

      // La respuesta de "caro" llega después de borrar hasta "c".
      await tester.enterText(find.byType(TextField), 'c');
      await tester.pump(const Duration(seconds: 2));
      await tester.pumpAndSettle();

      expect(find.text('Carolina'), findsNothing);
      expect(find.textContaining('No encontramos a nadie'), findsNothing);
    });

    testWidgets('una búsqueda nueva reemplaza a la que sigue en curso',
        (tester) async {
      final social = _FakeSocialRepository(
        resultsByQuery: const {
          'caro': [UserSummary(userId: 'usr_7', username: 'caro', displayName: 'Carolina')],
          'cam': [UserSummary(userId: 'usr_8', username: 'cami', displayName: 'Camila')],
        },
        delayByQuery: const {'caro': Duration(seconds: 1)},
      );
      await _pumpWizard(
        tester,
        eventRepository: FakeEventRepository(),
        socialRepository: social,
      );
      await _goToStep2(tester);

      await tester.enterText(find.byType(TextField), 'caro');
      await tester.pump(const Duration(milliseconds: 400));
      await tester.enterText(find.byType(TextField), 'cam');
      await tester.pump(const Duration(milliseconds: 400));
      await tester.pump(const Duration(seconds: 2));
      await tester.pumpAndSettle();

      expect(social.queries, ['caro', 'cam']);
      expect(find.text('Camila'), findsOneWidget);
      expect(find.text('Carolina'), findsNothing);
    });

    testWidgets('espera 300 ms entre teclas y busca una sola vez',
        (tester) async {
      final social = _FakeSocialRepository();
      await _pumpWizard(
        tester,
        eventRepository: FakeEventRepository(),
        socialRepository: social,
      );
      await _goToStep2(tester);

      for (final text in const ['ca', 'car', 'caro']) {
        await tester.enterText(find.byType(TextField), text);
        await tester.pump(const Duration(milliseconds: 100));
      }
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pumpAndSettle();

      expect(social.queries, ['caro']);
    });

    testWidgets('el 429 muestra "Espera un momento y vuelve a intentarlo"',
        (tester) async {
      await _pumpWizard(
        tester,
        eventRepository: FakeEventRepository(),
        socialRepository: _FakeSocialRepository(
          searchFailure: const ServerFailure(message: searchRateLimitedMessage),
        ),
      );
      await _goToStep2(tester);

      await tester.enterText(find.byType(TextField), 'caro');
      await tester.pump(const Duration(milliseconds: 400));
      await tester.pumpAndSettle();

      expect(find.text('Espera un momento y vuelve a intentarlo'),
          findsOneWidget);
      expect(find.textContaining('No encontramos'), findsNothing);
    });
  });

  group('CreateEventScreen — invitaciones al crear (T035)', () {
    Future<void> pickTwoAndCreate(
      WidgetTester tester,
      FakeEventRepository repo,
    ) async {
      await _pumpWizard(
        tester,
        eventRepository: repo,
        socialRepository: _FakeSocialRepository(results: const [
          UserSummary(userId: 'usr_2', username: 'caro.mz',
              displayName: 'Carolina Martínez'),
          UserSummary(userId: 'usr_3', username: 'andres.r'),
        ]),
        withRoutes: true,
      );
      await _goToStep2(tester);
      await tester.enterText(find.byType(TextField), 'ca');
      await tester.pump(const Duration(milliseconds: 400));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Carolina Martínez'));
      await tester.pumpAndSettle();
      // Sin displayName se muestra el usuario.
      await tester.tap(find.text('andres.r'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Enviar 2 invitaciones'));
      await tester.pumpAndSettle();
    }

    testWidgets('envía una invitación por persona y abre el evento',
        (tester) async {
      final repo = FakeEventRepository();
      await pickTwoAndCreate(tester, repo);

      expect(repo.created, hasLength(1));
      expect(repo.sentInvitations, [('evt_1', 'usr_2'), ('evt_1', 'usr_3')]);
      expect(find.text('ruta /event-detail'), findsOneWidget);
    });

    testWidgets('si una invitación falla, lo dice y abre el evento igual',
        (tester) async {
      final repo = FakeEventRepository(failInvitationTo: {'usr_3'});
      await pickTwoAndCreate(tester, repo);

      expect(repo.sentInvitations, hasLength(2));
      expect(find.text('Evento creado, pero no se pudo enviar 1 invitación.'),
          findsOneWidget);
      expect(find.text('ruta /event-detail'), findsOneWidget);
    });
  });

  group('CreateEventScreen — envío del evento', () {
    testWidgets('crea el evento con el payload del ADR-001', (tester) async {
      final repo = FakeEventRepository(failCreate: true);
      await _pumpWizard(tester, eventRepository: repo);
      await _goToStep2(tester);

      await tester.tap(find.text('Crear evento'));
      await tester.pumpAndSettle();

      expect(repo.created, hasLength(1));
      final call = repo.created.single;
      expect(call.title, 'Los 30 de Marta');
      expect(call.details, isNotNull);
      expect(call.details!.startDate, isNotNull);
      expect(call.details!.endDate!.isAfter(call.details!.startDate!), isTrue);
      expect(call.details!.category, 'BIRTHDAY');
      expect(call.settings, isNotNull);
      expect(call.settings!.isPublic, isFalse);
      expect(call.settings!.hideAddressUntilAccepted, isTrue);
      expect(call.settings!.allowGuestInvites, isFalse);
    });

    testWidgets('refleja el tipo de evento elegido en el payload',
        (tester) async {
      final repo = FakeEventRepository(failCreate: true);
      await _pumpWizard(tester, eventRepository: repo);

      await tester.tap(find.widgetWithText(OptionChip, 'Boda'));
      await tester.pumpAndSettle();

      await _goToStep2(tester);
      await tester.tap(find.text('Crear evento'));
      await tester.pumpAndSettle();

      expect(repo.created.single.details!.category, 'WEDDING');
    });

    testWidgets('muestra el error del backend y permite reintentar',
        (tester) async {
      final repo = FakeEventRepository(failCreate: true);
      await _pumpWizard(tester, eventRepository: repo);
      await _goToStep2(tester);

      await tester.tap(find.text('Crear evento'));
      await tester.pumpAndSettle();

      expect(find.text('Sin backend disponible'), findsOneWidget);
      expect(find.text('Crear evento'), findsOneWidget);
    });
  });
}
