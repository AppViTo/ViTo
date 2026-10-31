import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vito_app/core/error/dio_error_handler.dart';
import 'package:vito_app/core/error/exceptions.dart';
import 'package:vito_app/features/mosaic/data/datasources/mosaic_remote_datasource.dart';
import 'package:vito_app/features/mosaic/data/models/mosaic_model.dart';
import 'package:vito_app/features/mosaic/domain/entities/mosaic.dart';

import 'fixture_loader.dart';

/// Responde con un fixture y guarda la petición, para comprobar ruta y body.
class _FixtureAdapter implements HttpClientAdapter {
  RequestOptions? request;
  String requestBody = '';
  String responseBody = '{}';
  int statusCode = 200;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    request = options;
    final buffer = <int>[];
    if (requestStream != null) {
      await for (final chunk in requestStream) {
        buffer.addAll(chunk);
      }
    }
    requestBody = utf8.decode(buffer, allowMalformed: true);

    return ResponseBody.fromString(
      responseBody,
      statusCode,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

String _raw(String relative) => File(fixturePath(relative)).readAsStringSync();

/// T-F05 — Contrato del mosaico (Ola 3, T-C07) contra `fixtures/mosaic/`.
void main() {
  const feedTypes = {
    'CHECK_IN',
    'POST',
    'PHOTO',
    'REACTION',
    'STORY',
    'LIVE_UPDATE',
    'ANNOUNCEMENT',
  };
  const gridTypes = {'CHECK_IN', 'POST', 'PHOTO', 'REACTION', 'STORY', 'HIGHLIGHT'};

  late _FixtureAdapter adapter;
  late MosaicRemoteDataSource dataSource;

  setUp(() {
    adapter = _FixtureAdapter();
    final dio = Dio(BaseOptions(baseUrl: 'http://localhost:8080/api/v1'));
    dio.httpClientAdapter = adapter;
    dataSource = MosaicRemoteDataSourceImpl(dio: dio);
  });

  void expectFeedItemRoundTrip(Map<String, dynamic> json, String path) {
    final model = MosaicFeedItemModel.fromJson(json);
    expect(model.id, json['id'], reason: path);
    expect(model.eventId, json['eventId'], reason: path);
    expect(model.userId, json['userId'], reason: path);
    expect(model.content, json['content'], reason: path);
    expect(model.mediaUrls, json['mediaUrls'], reason: path);
    expect(model.likesCount, json['likesCount'], reason: path);
    expect(model.commentsCount, json['commentsCount'], reason: path);
    expect(model.isLiked, json['isLiked'], reason: path);
    expect(model.timestamp, DateTime.parse(json['timestamp'] as String));
    expect(feedTypes.contains(json['type']), isTrue, reason: path);

    final back = model.toJson();
    expect(back['type'], json['type'], reason: '$path: type ida y vuelta');
    expect(back['id'], json['id']);
    expect(back['mediaUrls'], json['mediaUrls']);
  }

  group('MosaicFeedItem', () {
    for (final path in [
      'mosaic/createMosaicPost.201.json',
      'mosaic/getMosaicItem.200.json',
    ]) {
      test('$path se parsea con MosaicFeedItemModel', () {
        expectFeedItemRoundTrip(loadFixtureObject(path), path);
      });
    }

    test('listMosaicFeed.200.json es un array plano (ADR-002)', () {
      final list = loadFixtureList('mosaic/listMosaicFeed.200.json');
      expect(list, isNotEmpty);
      for (final json in list) {
        expectFeedItemRoundTrip(
            json as Map<String, dynamic>, 'listMosaicFeed.200.json');
      }
    });

    test('displayName null cae al username; CHECK_IN sin displayName', () {
      final list = loadFixtureList('mosaic/listMosaicFeed.200.json');
      final luis = MosaicFeedItemModel.fromJson(list[0] as Map<String, dynamic>);
      expect(luis.displayName, 'luis');
      expect(luis.avatar, isNull);

      final checkIn =
          MosaicFeedItemModel.fromJson(list[2] as Map<String, dynamic>);
      expect(checkIn.type, FeedItemType.checkIn);
      expect(checkIn.displayName, 'luis');
      expect(checkIn.mediaUrls, isEmpty);
    });
  });

  group('MosaicGrid', () {
    test('getMosaicGrid.200.json: celdas con type, position y null', () {
      final json = loadFixtureObject('mosaic/getMosaicGrid.200.json');
      final grid = MosaicGridModel.fromJson(json);

      expect(grid.eventId, json['eventId']);
      expect(grid.totalItems, 2);
      expect(grid.lastUpdatedIndex, 1);
      expect(grid.lastUpdatedAt, DateTime.parse('2026-10-16T22:20:00Z'));
      expect(grid.grid, hasLength(2));
      expect(grid.grid[1], [null, null]);

      final first = grid.grid[0][0]!;
      expect(first.type, MosaicItemType.photo);
      expect(first.mediaUrl, 'https://cdn.vito.com/evt_01H8X/portada.jpg');
      expect(first.position,
          const MosaicItemPosition(row: 0, col: 0, width: 1, height: 1));

      final second = grid.grid[0][1]!;
      expect(second.displayName, 'luis');
      expect(second.position.col, 1);
      expect(second.likesCount, 3);

      for (final row in json['grid'] as List) {
        for (final cell in row as List) {
          if (cell == null) continue;
          expect(gridTypes.contains((cell as Map)['type']), isTrue);
        }
      }

      final back = grid.toJson();
      final backCell = (back['grid'] as List)[0][0] as Map<String, dynamic>;
      final cell = ((json['grid'] as List)[0] as List)[0] as Map;
      expect(backCell['type'], cell['type']);
      expect(backCell['position'], cell['position']);
      expect((back['grid'] as List)[1], [null, null]);
    });

    test('initializeMosaicGrid.201.json: grilla vacía', () {
      final grid = MosaicGridModel.fromJson(
          loadFixtureObject('mosaic/initializeMosaicGrid.201.json'));
      expect(grid.totalItems, 0);
      expect(grid.lastUpdatedIndex, -1);
      expect(grid.grid.expand((row) => row).every((cell) => cell == null),
          isTrue);
    });
  });

  group('MosaicLikeState', () {
    for (final path in [
      'mosaic/likeMosaicItem.200.json',
      'mosaic/unlikeMosaicItem.200.json',
    ]) {
      test('$path se parsea con MosaicLikeStateModel', () {
        final json = loadFixtureObject(path);
        final state = MosaicLikeStateModel.fromJson(json);
        expect(state.itemId, json['itemId']);
        expect(state.likesCount, json['likesCount']);
        expect(state.isLiked, json['isLiked']);
        expect(state.toJson(), json);
      });
    }
  });

  group('Datasource contra los fixtures', () {
    test('createMosaicPost envía el request del fixture (ADR-016)', () async {
      final request = loadFixtureObject('mosaic/createMosaicPost.request.json');
      adapter.statusCode = 201;
      adapter.responseBody = _raw('mosaic/createMosaicPost.201.json');

      final item = await dataSource.addPost(
        eventId: request['eventId'] as String,
        mediaId: request['mediaId'] as String,
        content: request['content'] as String?,
      );

      expect(adapter.request!.method, 'POST');
      expect(adapter.request!.uri.path, '/api/v1/mosaic/post');
      expect(jsonDecode(adapter.requestBody), request);
      expect(item.id, 'mos_01H8X2K4QJ');
      expect(item.type, FeedItemType.photo);
    });

    test('createMosaicPost sin texto no envía content ni userId', () async {
      adapter.statusCode = 201;
      adapter.responseBody = _raw('mosaic/createMosaicPost.201.json');

      await dataSource.addPost(eventId: 'evt_1', mediaId: 'med_1');

      expect(jsonDecode(adapter.requestBody),
          {'eventId': 'evt_1', 'mediaId': 'med_1'});
    });

    test('likeMosaicItem lee MosaicLikeState', () async {
      adapter.responseBody = _raw('mosaic/likeMosaicItem.200.json');

      final state = await dataSource.likePost('mos_01H8X2K4QK');

      expect(adapter.request!.method, 'POST');
      expect(adapter.request!.uri.path, '/api/v1/mosaic/item/mos_01H8X2K4QK/like');
      expect(state.likesCount, 4);
      expect(state.isLiked, isTrue);
    });

    test('unlikeMosaicItem lee MosaicLikeState', () async {
      adapter.responseBody = _raw('mosaic/unlikeMosaicItem.200.json');

      final state = await dataSource.unlikePost('mos_01H8X2K4QK');

      expect(adapter.request!.method, 'DELETE');
      expect(adapter.request!.uri.path, '/api/v1/mosaic/item/mos_01H8X2K4QK/like');
      expect(state.likesCount, 3);
      expect(state.isLiked, isFalse);
    });

    test('listMosaicFeed y getMosaicGrid usan las rutas del contrato', () async {
      adapter.responseBody = _raw('mosaic/listMosaicFeed.200.json');
      final feed = await dataSource.getEventFeed(eventId: 'evt_01H8X2K4QJ');
      expect(adapter.request!.uri.path, '/api/v1/mosaic/feed/evt_01H8X2K4QJ');
      expect(adapter.request!.uri.queryParameters, {'page': '0', 'size': '20'});
      expect(feed, hasLength(3));

      adapter.responseBody = _raw('mosaic/getMosaicGrid.200.json');
      final grid = await dataSource.getEventGrid('evt_01H8X2K4QJ');
      expect(adapter.request!.uri.path, '/api/v1/mosaic/grid/evt_01H8X2K4QJ');
      expect(grid.totalItems, 2);
    });

    test('createMosaicPost.400.json se traduce en ValidationException',
        () async {
      adapter.statusCode = 400;
      adapter.responseBody = _raw('mosaic/createMosaicPost.400.json');

      expect(
        () => dataSource.addPost(eventId: 'evt_1', mediaId: 'med_video'),
        throwsA(isA<ValidationException>().having((e) => e.message, 'message',
            'Solo se pueden publicar imágenes JPEG, PNG o WebP')),
      );
    });
  });

  group('Errores (ADR-003)', () {
    final dir = Directory(fixturePath('mosaic'));
    final errorFixtures = dir
        .listSync()
        .whereType<File>()
        .map((file) => file.uri.pathSegments.last)
        .where((name) => RegExp(r'\.[45]\d\d\.json$').hasMatch(name))
        .toList()
      ..sort();

    test('hay fixtures de error para todas las operaciones', () {
      final operations =
          errorFixtures.map((name) => name.split('.').first).toSet();
      expect(operations, {
        'createMosaicPost',
        'getMosaicGrid',
        'getMosaicItem',
        'initializeMosaicGrid',
        'likeMosaicItem',
        'listMosaicFeed',
        'removeMosaicItem',
        'unlikeMosaicItem',
      });
    });

    for (final name in errorFixtures) {
      test('mosaic/$name cumple el envelope y se parsea', () {
        final json = loadFixtureObject('mosaic/$name');
        final status = int.parse(name.split('.')[1]);

        expect(json['error'], isA<String>(), reason: name);
        expect(json['message'], isA<String>(), reason: name);
        expect((json['message'] as String).isNotEmpty, isTrue, reason: name);
        expect(json['status'], status, reason: name);
        expect(DateTime.parse(json['timestamp'] as String).isUtc, isTrue,
            reason: name);

        final exception = handleDioError(DioException(
          requestOptions: RequestOptions(path: '/mosaic'),
          response: Response(
            requestOptions: RequestOptions(path: '/mosaic'),
            statusCode: status,
            data: json,
          ),
        ));
        final message = switch (exception) {
          AuthenticationException(:final message) => message,
          ValidationException(:final message) => message,
          ServerException(:final message) => message,
          _ => null,
        };
        expect(message, json['message'], reason: name);
      });
    }

    test('removeMosaicItem.204.json no tiene cuerpo', () {
      expect(jsonDecode(_raw('mosaic/removeMosaicItem.204.json')), isNull);
    });
  });
}
