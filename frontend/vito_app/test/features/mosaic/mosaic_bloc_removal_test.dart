import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/features/mosaic/data/models/mosaic_model.dart';
import 'package:vito_app/features/mosaic/domain/entities/mosaic.dart';
import 'package:vito_app/features/mosaic/presentation/bloc/mosaic_bloc.dart';

import '../../contract/fixture_loader.dart';
import 'fakes.dart';

const _eventId = 'evt_01H8X2K4QJ';
const _removedId = 'mos_01H8X2K4QJ';

/// Moderación del organizador (T-F10, excepción A3) con los fixtures de la
/// Ola 3: la foto sale del feed y su celda de la grilla vuelve a `null`.
void main() {
  Future<MosaicBloc> blocWithFeed(FakeMosaicRepository repository) async {
    final bloc = MosaicBloc(repository: repository);
    final loaded = expectLater(
      bloc.stream,
      emitsThrough(isA<MosaicFeedLoaded>()),
    );
    bloc.add(const LoadEventFeed(_eventId));
    await loaded;
    return bloc;
  }

  Future<MosaicBloc> blocWithGrid(FakeMosaicRepository repository) async {
    final bloc = MosaicBloc(repository: repository);
    final loaded = expectLater(
      bloc.stream,
      emitsThrough(isA<MosaicGridLoaded>()),
    );
    bloc.add(const LoadEventGrid(_eventId));
    await loaded;
    return bloc;
  }

  group('ITEM_REMOVED por WebSocket', () {
    test('quita el ítem del feed', () async {
      final repository = FakeMosaicRepository(feed: feedFixture());
      final bloc = await blocWithFeed(repository);
      bloc.add(const ConnectToWebSocket(eventId: _eventId));
      await pumpEventQueue();
      expect(repository.connectedEvents, [_eventId]);

      final removed = expectLater(
        bloc.stream,
        emits(isA<MosaicFeedLoaded>().having(
          (s) => s.feed.map((item) => item.id).toList(),
          'ids',
          ['mos_01H8X2K4QK', 'mos_01H8X2K4QM'],
        )),
      );
      repository.itemRemovedController.add(_removedId);
      await removed;

      await bloc.close();
    });

    test('deja su celda de la grilla en null', () async {
      final repository = FakeMosaicRepository(grid: gridFixture());
      final bloc = await blocWithGrid(repository);
      bloc.add(const ConnectToWebSocket(eventId: _eventId));
      await pumpEventQueue();

      final removed = expectLater(
        bloc.stream,
        emits(isA<MosaicGridLoaded>()
            .having((s) => s.grid.grid[0][0], 'celda [0][0]', isNull)
            .having((s) => occupiedCellIds(s.grid), 'ocupadas',
                ['mos_01H8X2K4QK'])
            .having((s) => s.grid.totalItems, 'totalItems', 1)),
      );
      repository.itemRemovedController.add(_removedId);
      await removed;

      await bloc.close();
    });

    test('un itemId que no está en el estado no emite nada', () async {
      final repository = FakeMosaicRepository(feed: feedFixture());
      final bloc = await blocWithFeed(repository);
      final before = bloc.state;

      bloc.add(const MosaicItemRemovedReceived('mos_desconocido'));
      await pumpEventQueue();

      expect(bloc.state, same(before));
      await bloc.close();
    });
  });

  group('otros frames del WebSocket', () {
    test('LIKE_UPDATE cambia el total del ítem y conserva isLiked', () async {
      final repository = FakeMosaicRepository(feed: feedFixture());
      final bloc = await blocWithFeed(repository);
      bloc.add(const ConnectToWebSocket(eventId: _eventId));
      await pumpEventQueue();

      final updated = expectLater(
        bloc.stream,
        emits(isA<MosaicFeedLoaded>().having(
          (s) => s.feed.first,
          'primer ítem',
          isA<MosaicFeedItem>()
              .having((i) => i.id, 'id', 'mos_01H8X2K4QK')
              .having((i) => i.likesCount, 'likesCount', 7)
              .having((i) => i.isLiked, 'isLiked', isTrue),
        )),
      );
      repository.likeController.add(const MosaicLikeCountUpdate(
        itemId: 'mos_01H8X2K4QK',
        likesCount: 7,
      ));
      await updated;

      await bloc.close();
    });

    test('NEW_ITEM entra arriba del feed', () async {
      final repository = FakeMosaicRepository(feed: feedFixture());
      final bloc = await blocWithFeed(repository);
      bloc.add(const ConnectToWebSocket(eventId: _eventId));
      await pumpEventQueue();
      // Mismo payload que createMosaicPost.201, con un id que no está en el feed.
      final item = MosaicFeedItemModel.fromJson({
        ...loadFixtureObject('mosaic/createMosaicPost.201.json'),
        'id': 'mos_01H8X2K4QZ',
      });

      final added = expectLater(
        bloc.stream,
        emits(isA<MosaicFeedLoaded>()
            .having((s) => s.feed.first.id, 'primero', item.id)
            .having((s) => s.feed, 'feed', hasLength(4))),
      );
      repository.feedController.add(item);
      await added;

      await bloc.close();
    });

    test('GRID_UPDATE reemplaza la grilla cargada', () async {
      final repository = FakeMosaicRepository(grid: gridFixture());
      final bloc = await blocWithGrid(repository);
      bloc.add(const ConnectToWebSocket(eventId: _eventId));
      await pumpEventQueue();
      const empty = MosaicGrid(eventId: _eventId, grid: [
        [null, null],
      ]);

      final replaced = expectLater(
        bloc.stream,
        emits(isA<MosaicGridLoaded>().having((s) => s.grid, 'grid', empty)),
      );
      repository.gridController.add(empty);
      await replaced;

      await bloc.close();
    });
  });

  group('RemoveMosaicItem', () {
    test('204: llama al repositorio y quita el ítem del feed', () async {
      final repository = FakeMosaicRepository(feed: feedFixture());
      final bloc = await blocWithFeed(repository);

      final removed = expectLater(
        bloc.stream,
        emits(isA<MosaicFeedLoaded>().having(
          (s) => s.feed.any((item) => item.id == _removedId),
          'contiene el retirado',
          isFalse,
        )),
      );
      bloc.add(const RemoveMosaicItem(_removedId));
      await removed;

      expect(repository.removed, [_removedId]);
      await bloc.close();
    });

    test('204: vacía la celda de la grilla', () async {
      final repository = FakeMosaicRepository(grid: gridFixture());
      final bloc = await blocWithGrid(repository);

      final removed = expectLater(
        bloc.stream,
        emits(isA<MosaicGridLoaded>()
            .having((s) => s.grid.grid[0][0], 'celda [0][0]', isNull)),
      );
      bloc.add(const RemoveMosaicItem(_removedId));
      await removed;

      await bloc.close();
    });

    test('403: avisa con el message del envelope y conserva el feed', () async {
      final message = loadFixtureObject('mosaic/removeMosaicItem.403.json')
          ['message'] as String;
      final repository = FakeMosaicRepository(
        feed: feedFixture(),
        removeFailure: ServerFailure(message: message),
      );
      final bloc = await blocWithFeed(repository);
      final before = bloc.state as MosaicFeedLoaded;

      final failed = expectLater(
        bloc.stream,
        emitsInOrder(<Object>[
          isA<MosaicItemRemovalFailed>()
              .having((s) => s.message, 'message', message)
              .having((s) => s.itemId, 'itemId', _removedId),
          isA<MosaicFeedLoaded>()
              .having((s) => s.feed, 'feed', hasLength(before.feed.length)),
        ]),
      );
      bloc.add(const RemoveMosaicItem(_removedId));
      await failed;

      expect(bloc.state, before);
      await bloc.close();
    });
  });
}
