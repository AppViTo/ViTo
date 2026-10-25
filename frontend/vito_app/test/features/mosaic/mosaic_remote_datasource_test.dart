import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/core/error/exceptions.dart';
import 'package:vito_app/features/mosaic/data/datasources/mosaic_remote_datasource.dart';

import '../../contract/fixture_loader.dart';

/// Responde siempre con [statusCode] y [body] (JSON) y guarda la petición.
class _RecordingAdapter implements HttpClientAdapter {
  RequestOptions? request;
  int statusCode = 204;
  Map<String, dynamic>? body;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    request = options;
    final payload = body;
    if (payload == null) {
      return ResponseBody.fromString('', statusCode);
    }
    return ResponseBody.fromString(
      jsonEncode(payload),
      statusCode,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

/// `removeMosaicItem` (Ola 3, T-F10) contra los fixtures del contrato.
void main() {
  late _RecordingAdapter adapter;
  late MosaicRemoteDataSource dataSource;

  setUp(() {
    adapter = _RecordingAdapter();
    final dio = Dio(BaseOptions(baseUrl: 'http://localhost:8080/api/v1'));
    dio.httpClientAdapter = adapter;
    dataSource = MosaicRemoteDataSourceImpl(dio: dio);
  });

  test('usa DELETE /mosaic/item/{itemId} y el 204 termina sin error', () async {
    adapter.statusCode = 204;

    await dataSource.removeMosaicItem('mos_01H8X2K4QJ');

    expect(adapter.request!.method, 'DELETE');
    expect(adapter.request!.uri.path, '/api/v1/mosaic/item/mos_01H8X2K4QJ');
    expect(adapter.request!.data, isNull);
  });

  test('403 del no organizador lanza ServerException con el message', () async {
    final fixture = loadFixtureObject('mosaic/removeMosaicItem.403.json');
    adapter
      ..statusCode = 403
      ..body = fixture;

    await expectLater(
      dataSource.removeMosaicItem('mos_01H8X2K4QJ'),
      throwsA(
        isA<ServerException>()
            .having((e) => e.statusCode, 'statusCode', 403)
            .having((e) => e.message, 'message', fixture['message']),
      ),
    );
  });

  test('404 de una foto ya retirada lanza ServerException con el message',
      () async {
    final fixture = loadFixtureObject('mosaic/removeMosaicItem.404.json');
    adapter
      ..statusCode = 404
      ..body = fixture;

    await expectLater(
      dataSource.removeMosaicItem('mos_01H8X2K4QJ'),
      throwsA(
        isA<ServerException>()
            .having((e) => e.statusCode, 'statusCode', 404)
            .having((e) => e.message, 'message', fixture['message']),
      ),
    );
  });

  test('401 lanza AuthenticationException con el message', () async {
    final fixture = loadFixtureObject('mosaic/removeMosaicItem.401.json');
    adapter
      ..statusCode = 401
      ..body = fixture;

    await expectLater(
      dataSource.removeMosaicItem('mos_01H8X2K4QJ'),
      throwsA(
        isA<AuthenticationException>()
            .having((e) => e.message, 'message', fixture['message']),
      ),
    );
  });
}
