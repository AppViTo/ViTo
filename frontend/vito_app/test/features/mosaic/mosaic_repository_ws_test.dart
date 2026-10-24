import 'dart:async';
import 'dart:convert';

import 'package:dartz/dartz.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/core/error/exceptions.dart';
import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/core/network/ws_connection.dart';
import 'package:vito_app/features/mosaic/data/datasources/mosaic_remote_datasource.dart';
import 'package:vito_app/features/mosaic/data/repositories/mosaic_repository_impl.dart';
import 'package:vito_app/features/mosaic/domain/entities/mosaic.dart';

import '../../contract/fixture_loader.dart';
import '../events/fakes.dart';

class _FakeSocket implements WsSocket {
  final controller = StreamController<dynamic>();
  Uri? uri;

  @override
  Stream<dynamic> get stream => controller.stream;

  @override
  void add(dynamic data) {}

  @override
  Future<void> close([int? code, String? reason]) async {}

  @override
  int? get closeCode => null;
}

/// Solo implementa `removeMosaicItem`; lanza [error] si no es `null`.
class _RemovingRemoteDataSource extends Fake implements MosaicRemoteDataSource {
  Exception? error;
  final List<String> removed = <String>[];

  @override
  Future<void> removeMosaicItem(String itemId) async {
    removed.add(itemId);
    final failure = error;
    if (failure != null) throw failure;
  }
}

Failure _failureOf<T>(Either<Failure, T> result) =>
    result.fold((failure) => failure, (_) => throw StateError('era Right'));

/// Frames de `/ws/mosaic/{eventId}` (asyncapi.yaml: planos, sin `data`) y
/// `removeMosaicItem` en el repositorio (T-F10).
void main() {
  late List<_FakeSocket> sockets;
  late _RemovingRemoteDataSource remote;
  late MosaicRepositoryImpl repository;

  setUp(() {
    sockets = [];
    remote = _RemovingRemoteDataSource();
    repository = MosaicRepositoryImpl(
      remoteDataSource: remote,
      mediaDataSource: FakeMediaRemoteDataSource(),
      wsConnection: WsConnection(
        tokenProvider: () async => 'access-123',
        refreshAccessToken: () async => null,
        socketFactory: (uri) {
          final socket = _FakeSocket()..uri = uri;
          sockets.add(socket);
          return socket;
        },
      ),
    );
  });

  tearDown(() => repository.dispose());

  Future<_FakeSocket> connect() async {
    repository.connectToWebSocket('evt_01H8X2K4QJ');
    await pumpEventQueue();
    expect(sockets, hasLength(1));
    return sockets.single;
  }

  group('WebSocket', () {
    test('conecta a /ws/mosaic/{eventId}', () async {
      final socket = await connect();

      expect(socket.uri!.path, '/ws/mosaic/evt_01H8X2K4QJ');
      expect(socket.uri!.queryParameters, {'token': 'access-123'});
    });

    test('NEW_ITEM publica el MosaicFeedItem de `item`', () async {
      final socket = await connect();
      final item = loadFixtureObject('mosaic/createMosaicPost.201.json');
      final received = expectLater(
        repository.feedStream,
        emits(isA<MosaicFeedItem>()
            .having((i) => i.id, 'id', item['id'])
            .having((i) => i.mediaUrls, 'mediaUrls', item['mediaUrls'])),
      );

      socket.controller.add(jsonEncode({'type': 'NEW_ITEM', 'item': item}));

      await received;
    });

    test('GRID_UPDATE publica la grilla con sus celdas', () async {
      final socket = await connect();
      final grid = loadFixtureObject('mosaic/getMosaicGrid.200.json');
      final received = expectLater(
        repository.gridStream,
        emits(isA<MosaicGrid>()
            .having((g) => g.eventId, 'eventId', 'evt_01H8X2K4QJ')
            .having((g) => g.grid[0][0]?.id, 'celda [0][0]', 'mos_01H8X2K4QJ')
            .having((g) => g.grid[1][0], 'celda [1][0]', isNull)
            .having((g) => g.totalItems, 'totalItems', 2)),
      );

      socket.controller.add(jsonEncode({
        'type': 'GRID_UPDATE',
        'eventId': grid['eventId'],
        'grid': grid['grid'],
      }));

      await received;
    });

    test('LIKE_UPDATE publica itemId y likesCount', () async {
      final socket = await connect();
      final received = expectLater(
        repository.likeStream,
        emits(const MosaicLikeCountUpdate(
          itemId: 'mos_01H8X2K4QK',
          likesCount: 4,
        )),
      );

      socket.controller.add(jsonEncode({
        'type': 'LIKE_UPDATE',
        'itemId': 'mos_01H8X2K4QK',
        'likesCount': 4,
      }));

      await received;
    });

    test('ITEM_REMOVED publica el itemId', () async {
      final socket = await connect();
      final removed = expectLater(
        repository.itemRemovedStream,
        emits('mos_01H8X2K4QJ'),
      );

      socket.controller.add(jsonEncode({
        'type': 'ITEM_REMOVED',
        'eventId': 'evt_01H8X2K4QJ',
        'itemId': 'mos_01H8X2K4QJ',
      }));

      await removed;
    });

    test('un frame mal formado o con el formato viejo se descarta', () async {
      final socket = await connect();
      final removed = <String>[];
      final likes = <MosaicLikeCountUpdate>[];
      final removedSub = repository.itemRemovedStream.listen(removed.add);
      final likesSub = repository.likeStream.listen(likes.add);

      socket.controller
        ..add('no es json')
        ..add(jsonEncode({'type': 'ITEM_REMOVED', 'eventId': 'evt_1'}))
        ..add(jsonEncode({
          'type': 'LIKE_UPDATE',
          'data': {'likesCount': 4},
        }));
      await pumpEventQueue();

      expect(removed, isEmpty);
      expect(likes, isEmpty);
      await removedSub.cancel();
      await likesSub.cancel();
    });
  });

  group('removeMosaicItem', () {
    test('204 devuelve Right', () async {
      final result = await repository.removeMosaicItem('mos_01H8X2K4QJ');

      expect(result.isRight(), isTrue);
      expect(remote.removed, ['mos_01H8X2K4QJ']);
    });

    test('403 se vuelve AuthorizationFailure con el message', () async {
      final message =
          loadFixtureObject('mosaic/removeMosaicItem.403.json')['message']
              as String;
      remote.error = ServerException(message: message, statusCode: 403);

      final failure =
          _failureOf(await repository.removeMosaicItem('mos_01H8X2K4QJ'));

      expect(failure, isA<AuthorizationFailure>());
      expect(failure.message, message);
    });

    test('404 se vuelve NotFoundFailure con el message', () async {
      final message =
          loadFixtureObject('mosaic/removeMosaicItem.404.json')['message']
              as String;
      remote.error = ServerException(message: message, statusCode: 404);

      final failure =
          _failureOf(await repository.removeMosaicItem('mos_01H8X2K4QJ'));

      expect(failure, isA<NotFoundFailure>());
      expect(failure.message, message);
    });
  });
}
