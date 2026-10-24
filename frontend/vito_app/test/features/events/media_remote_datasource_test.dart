import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/core/error/exceptions.dart';
import 'package:vito_app/features/events/data/datasources/media_remote_datasource.dart';

class _RecordingAdapter implements HttpClientAdapter {
  RequestOptions? request;
  String requestBody = '';
  String responseBody = '{}';
  int statusCode = 201;

  _RecordingAdapter();

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

void main() {
  late _RecordingAdapter adapter;
  late MediaRemoteDataSource dataSource;

  setUp(() {
    adapter = _RecordingAdapter();
    final dio = Dio(BaseOptions(baseUrl: 'http://localhost:8080/api/v1'));
    dio.httpClientAdapter = adapter;
    dataSource = MediaRemoteDataSourceImpl(dio: dio);
  });

  test('usa POST /media/upload con multipart y los campos del contrato', () async {
    adapter.responseBody = jsonEncode({
      'id': 'med_1',
      'eventId': 'evt_1',
      'status': 'UPLOADED',
      'location': {'url': 'https://cdn.vito.app/evt_1/portada.jpg'},
    });

    final url = await dataSource.uploadEventCover(
      eventId: 'evt_1',
      bytes: Uint8List.fromList([1, 2, 3]),
      fileName: 'portada.jpg',
      mimeType: 'image/jpeg',
    );

    expect(adapter.request!.method, 'POST');
    expect(adapter.request!.uri.path, '/api/v1/media/upload');

    final rawContentType = adapter.request!.headers[Headers.contentTypeHeader];
    final contentType =
        rawContentType is List ? rawContentType.join(',') : '$rawContentType';
    expect(contentType, startsWith('multipart/form-data'));

    final body = adapter.requestBody.toLowerCase();
    expect(body, contains('name="eventid"'));
    expect(body, contains('evt_1'));
    expect(body, contains('name="file"'));
    expect(body, contains('filename="portada.jpg"'));
    expect(body, contains('content-type: image/jpeg'));

    expect(url, 'https://cdn.vito.app/evt_1/portada.jpg');
  });

  test('uploadMedia devuelve el Media.id para createMosaicPost (ADR-016)',
      () async {
    adapter.responseBody = jsonEncode({
      'id': 'med_01H8X2K4QJ',
      'eventId': 'evt_1',
      'status': 'UPLOADED',
      'location': {'url': 'https://cdn.vito.app/evt_1/foto.webp'},
    });

    final mediaId = await dataSource.uploadMedia(
      eventId: 'evt_1',
      bytes: Uint8List.fromList([1, 2, 3]),
      fileName: 'foto.webp',
      mimeType: 'image/webp',
    );

    expect(adapter.request!.method, 'POST');
    expect(adapter.request!.uri.path, '/api/v1/media/upload');
    final body = adapter.requestBody.toLowerCase();
    expect(body, contains('name="eventid"'));
    expect(body, contains('name="file"'));
    expect(body, contains('content-type: image/webp'));
    expect(mediaId, 'med_01H8X2K4QJ');
  });

  test('uploadMedia devuelve cadena vacía si la respuesta no trae id',
      () async {
    adapter.responseBody = jsonEncode({'status': 'UPLOADED'});

    final mediaId = await dataSource.uploadMedia(
      eventId: 'evt_1',
      bytes: Uint8List.fromList([1]),
      fileName: 'foto.png',
      mimeType: 'image/png',
    );

    expect(mediaId, isEmpty);
  });

  test('devuelve cadena vacía si la respuesta no trae location.url', () async {
    adapter.responseBody = jsonEncode({'id': 'med_1'});

    final url = await dataSource.uploadEventCover(
      eventId: 'evt_1',
      bytes: Uint8List.fromList([1]),
      fileName: 'portada.png',
      mimeType: 'image/png',
    );

    expect(url, isEmpty);
  });

  test('lee message del envelope de error (CONVENTIONS §3.3)', () async {
    adapter.statusCode = 400;
    adapter.responseBody = jsonEncode({
      'error': 'VALIDATION_ERROR',
      'message': 'El archivo supera los 10 MB',
      'status': 400,
      'timestamp': '2026-09-25T14:03:22Z',
    });

    expect(
      () => dataSource.uploadEventCover(
        eventId: 'evt_1',
        bytes: Uint8List.fromList([1]),
        fileName: 'grande.jpg',
        mimeType: 'image/jpeg',
      ),
      throwsA(isA<ValidationException>()
          .having((e) => e.message, 'message', 'El archivo supera los 10 MB')),
    );
  });

  test('401 se traduce en AuthenticationException', () async {
    adapter.statusCode = 401;
    adapter.responseBody = jsonEncode({
      'error': 'UNAUTHORIZED',
      'message': 'Token expirado',
      'status': 401,
    });

    expect(
      () => dataSource.uploadEventCover(
        eventId: 'evt_1',
        bytes: Uint8List.fromList([1]),
        fileName: 'portada.jpg',
        mimeType: 'image/jpeg',
      ),
      throwsA(isA<AuthenticationException>()),
    );
  });
}
