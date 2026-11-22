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

class _FakeSocialRepository implements SocialRepository {
  final Map<String, SocialProfile> profiles;
  final Failure? profileFailure;
  final Failure? followFailure;

  /// Si se da, follow y unfollow esperan a que se complete (para ver el
  /// estado optimista mientras la petición está en curso).
  Completer<void>? gate;
  final List<String> calls = [];

  _FakeSocialRepository({
    this.profiles = const {},
    this.profileFailure,
    this.followFailure,
  });

  @override
  Future<Either<Failure, SocialProfile>> getProfile(String userId) async {
    calls.add('profile $userId');
    final failure = profileFailure;
    if (failure != null) return Left(failure);
    return Right(profiles[userId]!);
  }

  Future<Either<Failure, void>> _act(String name, String userId) async {
    calls.add('$name $userId');
    await gate?.future;
    final failure = followFailure;
    return failure != null ? Left(failure) : const Right(null);
  }

  @override
  Future<Either<Failure, void>> follow(String userId) => _act('follow', userId);

  @override
  Future<Either<Failure, void>> unfollow(String userId) =>
      _act('unfollow', userId);

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
}) async {
  tester.view.physicalSize = const Size(800, 1800);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    BlocProvider<AuthBloc>(
      create: (_) => StubAuthBloc('usr_1'),
      child: MaterialApp(
        home: ProfilePage(userId: userId, repository: repository),
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
    expect(find.widgetWithText(OutlinedButton, 'Siguiendo'), findsOneWidget);
    expect(find.text('3'), findsOneWidget);

    // Dejar de seguir vuelve a 2.
    await tester.tap(find.widgetWithText(OutlinedButton, 'Siguiendo'));
    await tester.pumpAndSettle();
    expect(repository.calls.last, 'unfollow usr_2');
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
