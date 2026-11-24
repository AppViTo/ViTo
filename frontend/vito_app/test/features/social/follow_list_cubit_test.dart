import 'dart:async';

import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/features/social/presentation/bloc/follow_list_cubit.dart';

import 'fakes.dart';

/// T053: paginación infinita que pide hasta recibir una página vacía.
void main() {
  FollowListCubit cubitFor(FakeGraphRepository repository) => FollowListCubit(
        userId: 'usr_1',
        kind: FollowListKind.followers,
        repository: repository,
      );

  test('una página corta no es el final: sigue hasta una página vacía',
      () async {
    final repository = FakeGraphRepository(followerPages: [
      [for (var i = 0; i < 20; i++) person(i)],
      // Más corta que `size`: el backend omitió a alguien.
      [person(20), person(21)],
    ]);
    final cubit = cubitFor(repository);

    await cubit.load();
    expect(cubit.state.items, hasLength(20));
    expect(cubit.state.reachedEnd, isFalse);

    await cubit.loadMore();
    expect(cubit.state.items, hasLength(22));
    expect(cubit.state.reachedEnd, isFalse);

    await cubit.loadMore();
    expect(cubit.state.reachedEnd, isTrue);
    expect(repository.calls,
        ['followers usr_1 0', 'followers usr_1 1', 'followers usr_1 2']);

    // Ya no pide más.
    await cubit.loadMore();
    expect(repository.calls, hasLength(3));
  });

  test('no repite a alguien que aparece en dos páginas', () async {
    final repository = FakeGraphRepository(followerPages: [
      [person(1), person(2)],
      [person(2), person(3)],
    ]);
    final cubit = cubitFor(repository);

    await cubit.load();
    await cubit.loadMore();

    expect(cubit.state.items.map((u) => u.userId),
        ['usr_1', 'usr_2', 'usr_3']);
  });

  test('cerrar el cubit con la carga en curso no emite después de close',
      () async {
    final repository = FakeGraphRepository(followerPages: [
      [person(1)],
    ])
      ..gate = Completer<void>();
    final cubit = cubitFor(repository);

    final loading = cubit.load();
    await cubit.close();
    repository.gate!.complete();

    await expectLater(loading, completes);
    await expectLater(cubit.loadMore(), completes);
  });

  test('una recarga descarta la respuesta de la carga anterior', () async {
    final repository = FakeGraphRepository(followerPages: [
      [person(1)],
    ])
      ..gate = Completer<void>();
    final cubit = cubitFor(repository);
    addTearDown(cubit.close);

    final first = cubit.load();
    final second = cubit.load();
    repository.gate!.complete();
    await Future.wait([first, second]);

    expect(cubit.state.items.map((u) => u.userId), ['usr_1']);
  });

  test('un error en la primera página queda en el estado', () async {
    final cubit = cubitFor(FakeGraphRepository(
      listFailure: const ServerFailure(message: 'No pudimos cargar la lista'),
    ));

    await cubit.load();

    expect(cubit.state.loading, isFalse);
    expect(cubit.state.items, isEmpty);
    expect(cubit.state.error, 'No pudimos cargar la lista');
  });
}
