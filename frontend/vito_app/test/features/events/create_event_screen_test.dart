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
import 'package:vito_app/features/social/domain/entities/social_profile.dart';
import 'package:vito_app/features/social/domain/repositories/social_repository.dart';
import 'package:vito_app/features/social/domain/usecases/follow.dart';
import 'package:vito_app/features/social/domain/usecases/get_profile.dart';
import 'package:vito_app/features/social/domain/usecases/unfollow.dart';
import 'package:vito_app/features/social/presentation/bloc/social_bloc.dart';

import 'fakes.dart';

class _FakeSocialRepository implements SocialRepository {
  final List<SocialProfile> results;

  _FakeSocialRepository({this.results = const []});

  @override
  Future<Either<Failure, SocialProfile>> getProfile(String userId) async =>
      const Left(ServerFailure());

  @override
  Future<Either<Failure, List<SocialProfile>>> searchUsers({
    required String query,
    int page = 0,
    int size = 20,
  }) async =>
      Right(results);

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
    testWidgets('muestra el buscador, el enlace y el CTA por defecto',
        (tester) async {
      await _pumpWizard(
        tester,
        eventRepository: FakeEventRepository(),
      );
      await _goToStep2(tester);

      expect(find.byType(TextField), findsOneWidget);
      expect(find.text('COMPARTE EL ENLACE'), findsOneWidget);
      expect(find.text('Copiar enlace de invitación'), findsOneWidget);
      expect(find.text('Enviar por WhatsApp'), findsOneWidget);
      expect(find.text('Permitir que traigan acompañante'), findsOneWidget);
      expect(find.text('Crear evento'), findsOneWidget);
    });

    testWidgets('busca, añade un invitado y actualiza el CTA', (tester) async {
      await _pumpWizard(
        tester,
        eventRepository: FakeEventRepository(),
        socialRepository: _FakeSocialRepository(results: const [
          SocialProfile(
            userId: 'usr_1',
            username: 'beto',
            displayName: 'Beto Ramírez',
          ),
          SocialProfile(
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
          SocialProfile(userId: 'u1', username: 'a', displayName: 'Ana'),
          SocialProfile(userId: 'u2', username: 'b', displayName: 'Beto'),
          SocialProfile(userId: 'u3', username: 'c', displayName: 'Caro'),
          SocialProfile(userId: 'u4', username: 'd', displayName: 'Diego'),
          SocialProfile(userId: 'u5', username: 'e', displayName: 'Eva'),
        ]),
      );
      await _goToStep2(tester);

      await tester.enterText(find.byType(TextField), 'x');
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

      expect(find.textContaining('Sin coincidencias'), findsOneWidget);
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
