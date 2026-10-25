import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/features/auth/domain/entities/user.dart';
import 'package:vito_app/features/auth/domain/repositories/auth_repository.dart';
import 'package:vito_app/features/auth/domain/usecases/login.dart';
import 'package:vito_app/features/auth/domain/usecases/logout.dart';
import 'package:vito_app/features/auth/domain/usecases/register.dart';
import 'package:vito_app/features/auth/domain/usecases/restore_session.dart';
import 'package:vito_app/features/auth/presentation/bloc/auth_bloc.dart';
import 'package:vito_app/features/mosaic/presentation/bloc/mosaic_bloc.dart';
import 'package:vito_app/features/mosaic/presentation/pages/mosaic_page.dart';

import '../../contract/fixture_loader.dart';
import 'fakes.dart';

const _eventId = 'evt_01H8X2K4QJ';
const _organizerId = 'usr_01H8X2K4QJ';
const _removedId = 'mos_01H8X2K4QJ';

/// Primer ítem de `listMosaicFeed.200.json`, siempre visible en el feed.
const _feedFirstId = 'mos_01H8X2K4QK';

class _UnusedAuthRepository extends Fake implements AuthRepository {}

/// `AuthBloc` ya resuelto: autenticado como [userId] o, si es `null`, sin
/// sesión.
class _StubAuthBloc extends AuthBloc {
  _StubAuthBloc(String? userId, AuthRepository repository)
      : super(
          loginUseCase: LoginUseCase(repository),
          registerUseCase: RegisterUseCase(repository),
          logoutUseCase: LogoutUseCase(repository),
          restoreSessionUseCase: RestoreSessionUseCase(repository),
        ) {
    emit(userId == null
        ? const AuthUnauthenticated()
        : AuthAuthenticated(
            user: User(id: userId, username: 'user', email: 'user@vito.app'),
          ));
  }
}

Finder _removeButtons() => find.byTooltip('Retirar foto');

Finder _removeButtonFor(String itemId) =>
    find.byKey(ValueKey('remove-mosaic-item-$itemId'));

/// Acción "Retirar foto" del mosaico (T-F10, excepción A3): solo la ve el
/// organizador del evento, pide confirmación y muestra el `message` del error.
void main() {
  Future<FakeMosaicRepository> pumpMosaic(
    WidgetTester tester, {
    required String? currentUserId,
    String? organizerId = _organizerId,
    bool feedView = false,
    Failure? removeFailure,
  }) async {
    final repository = feedView
        ? FakeMosaicRepository(feed: feedFixture(), removeFailure: removeFailure)
        : FakeMosaicRepository(grid: gridFixture(), removeFailure: removeFailure);

    await tester.pumpWidget(
      MultiBlocProvider(
        providers: [
          BlocProvider<AuthBloc>(
            create: (_) =>
                _StubAuthBloc(currentUserId, _UnusedAuthRepository()),
          ),
          BlocProvider<MosaicBloc>(
            create: (_) => MosaicBloc(repository: repository),
          ),
        ],
        child: MaterialApp(
          home: MosaicPage(eventId: _eventId, organizerId: organizerId),
        ),
      ),
    );
    await tester.pumpAndSettle();

    if (feedView) {
      await tester.tap(find.byIcon(Icons.list));
      await tester.pumpAndSettle();
    }
    return repository;
  }

  group('visibilidad', () {
    testWidgets('el organizador ve "Retirar foto" en cada celda de la grilla',
        (tester) async {
      final repository =
          await pumpMosaic(tester, currentUserId: _organizerId);

      expect(_removeButtons(), findsNWidgets(2));
      expect(_removeButtonFor(_removedId), findsOneWidget);
      expect(repository.connectedEvents, [_eventId]);
    });

    testWidgets('el organizador ve "Retirar foto" en cada ítem del feed',
        (tester) async {
      await pumpMosaic(tester, currentUserId: _organizerId, feedView: true);

      // El ListView construye solo las tarjetas visibles.
      expect(_removeButtons(), findsWidgets);
      expect(_removeButtonFor(_feedFirstId), findsOneWidget);
    });

    testWidgets('un invitado no ve la acción en la grilla ni en el feed',
        (tester) async {
      await pumpMosaic(tester, currentUserId: 'usr_01H8X2K4QL');
      expect(_removeButtons(), findsNothing);

      await tester.tap(find.byIcon(Icons.list));
      await tester.pumpAndSettle();
      expect(_removeButtons(), findsNothing);
    });

    testWidgets('sin sesión no se ve la acción', (tester) async {
      await pumpMosaic(tester, currentUserId: null);

      expect(_removeButtons(), findsNothing);
    });

    testWidgets('sin organizerId del evento no se ve la acción',
        (tester) async {
      await pumpMosaic(tester, currentUserId: _organizerId, organizerId: null);

      expect(_removeButtons(), findsNothing);
    });
  });

  group('retirar', () {
    testWidgets('confirmar retira la foto y su celda desaparece',
        (tester) async {
      final repository =
          await pumpMosaic(tester, currentUserId: _organizerId);

      await tester.tap(_removeButtonFor(_removedId));
      await tester.pumpAndSettle();
      expect(find.text('¿Retirar esta foto?'), findsOneWidget);

      await tester.tap(find.text('Retirar'));
      await tester.pumpAndSettle();

      expect(repository.removed, [_removedId]);
      expect(_removeButtonFor(_removedId), findsNothing);
      expect(_removeButtons(), findsOneWidget);
    });

    testWidgets('cancelar no llama al endpoint', (tester) async {
      final repository =
          await pumpMosaic(tester, currentUserId: _organizerId);

      await tester.tap(_removeButtonFor(_removedId));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();

      expect(repository.removed, isEmpty);
      expect(_removeButtonFor(_removedId), findsOneWidget);
    });

    testWidgets('en el feed, confirmar quita el ítem', (tester) async {
      final repository = await pumpMosaic(
        tester,
        currentUserId: _organizerId,
        feedView: true,
      );

      await tester.tap(_removeButtonFor(_feedFirstId));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Retirar'));
      await tester.pumpAndSettle();

      expect(repository.removed, [_feedFirstId]);
      expect(_removeButtonFor(_feedFirstId), findsNothing);
    });

    testWidgets('un 403 muestra el message del envelope y conserva la foto',
        (tester) async {
      final message = loadFixtureObject('mosaic/removeMosaicItem.403.json')
          ['message'] as String;
      await pumpMosaic(
        tester,
        currentUserId: _organizerId,
        removeFailure: ServerFailure(message: message),
      );

      await tester.tap(_removeButtonFor(_removedId));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Retirar'));
      await tester.pumpAndSettle();

      expect(find.text(message), findsOneWidget);
      expect(_removeButtonFor(_removedId), findsOneWidget);
    });

    testWidgets('ITEM_REMOVED de otro cliente quita la celda',
        (tester) async {
      final repository =
          await pumpMosaic(tester, currentUserId: 'usr_01H8X2K4QL');

      repository.itemRemovedController.add(_removedId);
      await tester.pumpAndSettle();

      expect(find.byType(Image), findsOneWidget);
    });
  });
}
