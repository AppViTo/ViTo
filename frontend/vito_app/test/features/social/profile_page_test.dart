import 'dart:async';

import 'package:dartz/dartz.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/features/auth/presentation/bloc/auth_bloc.dart';
import 'package:vito_app/features/social/data/repositories/social_repository_impl.dart';
import 'package:vito_app/features/social/domain/entities/social_profile.dart';
import 'package:vito_app/features/social/domain/entities/user_summary.dart';
import 'package:vito_app/features/social/domain/repositories/social_repository.dart';
import 'package:vito_app/features/social/presentation/bloc/social_profile_cubit.dart';
import 'package:vito_app/features/social/presentation/pages/profile_page.dart';

import '../events/auth_stub.dart';

const _caro = SocialProfile(
  userId: 'usr_2',
  username: 'caro.mz',
  displayName: 'Carolina Martínez',
  followersCount: 2,
  followingCount: 1,
  isFollowing: false,
  isFollowedBy: true,
);

const _me = SocialProfile(
  userId: 'usr_1',
  username: 'alex_villa',
  displayName: 'Alex Villa',
  followersCount: 1,
  followingCount: 2,
);

/// Como el backend: follow y unfollow son idempotentes y cambian el perfil
/// que devuelve `getProfile` solo si cambia la relación.
class _FakeSocialRepository implements SocialRepository {
  final Map<String, SocialProfile> profiles;
  Failure? profileFailure;
  final Failure? followFailure;

  /// Si se da, follow y unfollow esperan a que se complete (para ver el
  /// estado optimista mientras la petición está en curso).
  Completer<void>? gate;

  /// Si se da, `getProfile` espera a que se complete.
  Completer<void>? profileGate;
  final List<String> calls = [];

  _FakeSocialRepository({
    Map<String, SocialProfile> profiles = const {},
    this.profileFailure,
    this.followFailure,
  }) : profiles = Map.of(profiles);

  @override
  Future<Either<Failure, SocialProfile>> getProfile(String userId) async {
    calls.add('profile $userId');
    await profileGate?.future;
    final failure = profileFailure;
    if (failure != null) return Left(failure);
    return Right(profiles[userId]!);
  }

  Future<Either<Failure, void>> _act(String name, String userId) async {
    calls.add('$name $userId');
    await gate?.future;
    final failure = followFailure;
    if (failure != null) return Left(failure);
    final profile = profiles[userId];
    final follow = name == 'follow';
    if (profile != null && profile.isFollowing != follow) {
      profiles[userId] = profile.copyWith(
        isFollowing: follow,
        followersCount: profile.followersCount + (follow ? 1 : -1),
      );
    }
    return const Right(null);
  }

  @override
  Future<Either<Failure, void>> follow(String userId) => _act('follow', userId);

  @override
  Future<Either<Failure, void>> unfollow(String userId) =>
      _act('unfollow', userId);

  @override
  Future<Either<Failure, List<UserSummary>>> getFollowers({
    required String userId,
    int page = 0,
    int size = 20,
  }) async =>
      const Right([]);

  @override
  Future<Either<Failure, List<UserSummary>>> getFollowing({
    required String userId,
    int page = 0,
    int size = 20,
  }) async =>
      const Right([]);

  @override
  Future<Either<Failure, List<UserSummary>>> searchUsers({
    required String query,
    int page = 0,
    int size = 20,
  }) async =>
      const Right([]);
}

Future<void> _pump(
  WidgetTester tester,
  _FakeSocialRepository repository, {
  String? userId,
  Listenable? reloadSignal,
}) async {
  tester.view.physicalSize = const Size(800, 1800);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    BlocProvider<AuthBloc>(
      create: (_) => StubAuthBloc('usr_1'),
      child: MaterialApp(
        home: ProfilePage(
          userId: userId,
          repository: repository,
          reloadSignal: reloadSignal,
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

/// T042 y T048 (Ola 2a): perfil con `getSocialProfile` y seguir optimista.
void main() {
  testWidgets('perfil ajeno: nombre, usuario, contadores y "Te sigue"',
      (tester) async {
    final repository = _FakeSocialRepository(profiles: {'usr_2': _caro});
    await _pump(tester, repository, userId: 'usr_2');

    expect(repository.calls, ['profile usr_2']);
    expect(find.text('Carolina Martínez'), findsOneWidget);
    expect(find.text('@caro.mz'), findsOneWidget);
    expect(find.text('Te sigue'), findsOneWidget);
    expect(find.text('Seguidores'), findsOneWidget);
    expect(find.text('2'), findsOneWidget);
    expect(find.text('Seguir'), findsOneWidget);
    // Ya no hay contadores inventados.
    expect(find.text('Posts'), findsNothing);
    expect(find.text('Streams'), findsNothing);
  });

  testWidgets('propio perfil: sin botón de seguir ni "Te sigue"',
      (tester) async {
    final repository = _FakeSocialRepository(profiles: {'usr_1': _me});
    await _pump(tester, repository);

    expect(repository.calls, ['profile usr_1']);
    expect(find.text('Alex Villa'), findsOneWidget);
    expect(find.text('Seguir'), findsNothing);
    expect(find.text('Siguiendo'), findsOneWidget); // el contador
    expect(find.text('Te sigue'), findsNothing);
    expect(find.text('Cerrar sesión'), findsOneWidget);
  });

  testWidgets('seguir actualiza el botón y el contador al instante',
      (tester) async {
    final repository = _FakeSocialRepository(profiles: {'usr_2': _caro})
      ..gate = Completer<void>();
    await _pump(tester, repository, userId: 'usr_2');

    await tester.tap(find.text('Seguir'));
    await tester.pump();

    // Optimista, con la petición aún en curso.
    expect(find.widgetWithText(OutlinedButton, 'Siguiendo'), findsOneWidget);
    expect(find.text('3'), findsOneWidget);
    expect(repository.calls.last, 'follow usr_2');

    repository.gate!.complete();
    await tester.pumpAndSettle();
    // Tras el 204 recarga el perfil.
    expect(repository.calls.last, 'profile usr_2');
    expect(find.widgetWithText(OutlinedButton, 'Siguiendo'), findsOneWidget);
    expect(find.text('3'), findsOneWidget);

    // Dejar de seguir vuelve a 2.
    await tester.tap(find.widgetWithText(OutlinedButton, 'Siguiendo'));
    await tester.pumpAndSettle();
    expect(repository.calls.sublist(repository.calls.length - 2),
        ['unfollow usr_2', 'profile usr_2']);
    expect(find.text('Seguir'), findsOneWidget);
    expect(find.text('2'), findsOneWidget);
  });

  testWidgets('si seguir falla, revierte el botón y el contador',
      (tester) async {
    final repository = _FakeSocialRepository(
      profiles: {'usr_2': _caro},
      followFailure: const ServerFailure(message: 'No se pudo seguir'),
    );
    await _pump(tester, repository, userId: 'usr_2');

    await tester.tap(find.text('Seguir'));
    await tester.pumpAndSettle();

    expect(find.text('No se pudo seguir'), findsOneWidget);
    expect(find.text('Seguir'), findsOneWidget);
    expect(find.text('2'), findsOneWidget);
  });

  testWidgets('tras el 204 se queda con el contador del backend, no con el '
      'optimista', (tester) async {
    // Ya lo seguía desde otro dispositivo: el follow es idempotente y el
    // backend sigue en 2 seguidores.
    final repository = _FakeSocialRepository(profiles: {
      'usr_2': _caro.copyWith(isFollowing: false),
    });
    await _pump(tester, repository, userId: 'usr_2');
    repository.profiles['usr_2'] = _caro.copyWith(isFollowing: true);

    await tester.tap(find.text('Seguir'));
    await tester.pumpAndSettle();

    expect(find.widgetWithText(OutlinedButton, 'Siguiendo'), findsOneWidget);
    expect(find.text('2'), findsOneWidget);
    expect(find.text('3'), findsNothing);
  });

  testWidgets('si el perfil propio falla, igual se puede cerrar sesión',
      (tester) async {
    final repository = _FakeSocialRepository(
      profileFailure: const ServerFailure(message: profileUnavailableMessage),
    );
    await _pump(tester, repository);

    expect(find.text(profileUnavailableMessage), findsOneWidget);
    expect(find.text('Reintentar'), findsOneWidget);
    expect(find.text('Cerrar sesión'), findsOneWidget);
  });

  testWidgets('un perfil ajeno que falla no ofrece cerrar sesión',
      (tester) async {
    final repository = _FakeSocialRepository(
      profileFailure: const ServerFailure(message: profileUnavailableMessage),
    );
    await _pump(tester, repository, userId: 'usr_2');

    expect(find.text('Cerrar sesión'), findsNothing);
  });

  testWidgets('el aviso de recarga trae los contadores nuevos sin spinner',
      (tester) async {
    final signal = ValueNotifier<int>(0);
    addTearDown(signal.dispose);
    final repository = _FakeSocialRepository(profiles: {'usr_1': _me});
    await _pump(tester, repository, reloadSignal: signal);
    expect(find.text('2'), findsOneWidget); // siguiendo

    repository.profiles['usr_1'] = _me.copyWith(followingCount: 5);
    repository.profileGate = Completer<void>();
    signal.value++;
    await tester.pump();

    // Mientras recarga, sigue el perfil a la vista.
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.text('Alex Villa'), findsOneWidget);

    repository.profileGate!.complete();
    await tester.pumpAndSettle();
    expect(repository.calls, ['profile usr_1', 'profile usr_1']);
    expect(find.text('5'), findsOneWidget);
  });

  testWidgets('si la recarga falla, queda el perfil que había',
      (tester) async {
    final signal = ValueNotifier<int>(0);
    addTearDown(signal.dispose);
    final repository = _FakeSocialRepository(profiles: {'usr_1': _me});
    await _pump(tester, repository, reloadSignal: signal);

    repository.profileFailure = const NetworkFailure(message: 'Sin conexión');
    signal.value++;
    await tester.pumpAndSettle();

    expect(find.text('Alex Villa'), findsOneWidget);
    expect(find.text('Reintentar'), findsNothing);
  });

  group('SocialProfileCubit', () {
    test('cerrar con la carga en curso no emite después de close', () async {
      final repository = _FakeSocialRepository(profiles: {'usr_2': _caro})
        ..profileGate = Completer<void>();
      final cubit = SocialProfileCubit(userId: 'usr_2', repository: repository);

      final loading = cubit.load();
      await cubit.close();
      repository.profileGate!.complete();

      await expectLater(loading, completes);
    });

    test('cerrar con un follow en curso no emite después de close', () async {
      final repository = _FakeSocialRepository(profiles: {'usr_2': _caro});
      final cubit = SocialProfileCubit(userId: 'usr_2', repository: repository);
      await cubit.load();

      repository.gate = Completer<void>();
      final following = cubit.toggleFollow();
      await cubit.close();
      repository.gate!.complete();

      await expectLater(following, completes);
      expect(repository.calls, ['profile usr_2', 'follow usr_2']);
    });
  });

  testWidgets('el 503 muestra "No pudimos cargar el perfil" y permite '
      'reintentar', (tester) async {
    final repository = _FakeSocialRepository(
      profileFailure: const ServerFailure(message: profileUnavailableMessage),
    );
    await _pump(tester, repository, userId: 'usr_2');

    expect(find.text('No pudimos cargar el perfil, intenta de nuevo'),
        findsOneWidget);
    await tester.tap(find.text('Reintentar'));
    await tester.pumpAndSettle();
    expect(repository.calls, ['profile usr_2', 'profile usr_2']);
  });
}
