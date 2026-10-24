import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/core/error/exceptions.dart';
import 'package:vito_app/features/events/data/datasources/event_remote_datasource.dart';

import '../../contract/fixture_loader.dart';

/// T-F08 — `listEvents` con `scope=mine|upcoming` (ADR-014), contra los
/// fixtures de T-C06 en `docs/api/fixtures/events/listEvents.*.json`.
class _RecordingAdapter implements HttpClientAdapter {
  RequestOptions? request;
  String responseBody = '[]';
  int statusCode = 200;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    request = options;
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

/// Cuerpo de respuesta HTTP tomado tal cual de un fixture de T-C06.
String _fixture(String relative) => relative.contains('.200.')
    ? jsonEncode(loadFixtureList(relative))
    : jsonEncode(loadFixtureObject(relative));

void main() {
  late _RecordingAdapter adapter;
  late EventRemoteDataSource dataSource;

  setUp(() {
    adapter = _RecordingAdapter();
    final dio = Dio(BaseOptions(baseUrl: 'http://localhost:8080/api/v1'));
    dio.httpClientAdapter = adapter;
    dataSource = EventRemoteDataSourceImpl(dio: dio);
  });

  group('getMyEvents', () {
    test('usa GET /events con scope=mine y parsea listEvents.mine.200.json',
        () async {
      adapter.responseBody = _fixture('events/listEvents.mine.200.json');
      final expected = loadFixtureList('events/listEvents.mine.200.json');

      final events = await dataSource.getMyEvents();

      expect(adapter.request!.method, 'GET');
      expect(adapter.request!.uri.path, '/api/v1/events');
      expect(adapter.request!.uri.queryParameters, {
        'scope': 'mine',
        'page': '0',
        'size': '20',
      });

      expect(events, hasLength(expected.length));
      final first = (expected.first as Map).cast<String, dynamic>();
      expect(events.first.id, first['id']);
      expect(events.first.title, first['title']);
      expect(events.first.organizerId, first['organizerId']);
    });

    test('propaga page y size', () async {
      await dataSource.getMyEvents(page: 2, size: 50);

      expect(adapter.request!.uri.queryParameters['scope'], 'mine');
      expect(adapter.request!.uri.queryParameters['page'], '2');
      expect(adapter.request!.uri.queryParameters['size'], '50');
    });
  });

  group('getUpcomingEvents', () {
    test(
        'usa GET /events con scope=upcoming (no /events/upcoming) y parsea '
        'listEvents.upcoming.200.json', () async {
      adapter.responseBody = _fixture('events/listEvents.upcoming.200.json');
      final expected = loadFixtureList('events/listEvents.upcoming.200.json');

      final events = await dataSource.getUpcomingEvents();

      expect(adapter.request!.method, 'GET');
      expect(adapter.request!.uri.path, '/api/v1/events');
      expect(adapter.request!.uri.queryParameters, {
        'scope': 'upcoming',
        'page': '0',
        'size': '20',
      });

      expect(
        events.map((e) => e.id).toList(),
        expected.map((e) => (e as Map)['id']).toList(),
      );
      for (final event in events) {
        expect(event.settings?.isPublic, isTrue,
            reason: 'ADR-014: upcoming solo lista eventos públicos');
      }
    });
  });

  group('errores', () {
    test('listEvents.400.json se traduce en ValidationException', () async {
      adapter.statusCode = 400;
      adapter.responseBody = _fixture('events/listEvents.400.json');
      final expected = loadFixtureObject('events/listEvents.400.json');

      expect(
        () => dataSource.getUpcomingEvents(),
        throwsA(isA<ValidationException>()
            .having((e) => e.message, 'message', expected['message'])),
      );
    });

    test('listEvents.401.json se traduce en AuthenticationException', () async {
      adapter.statusCode = 401;
      adapter.responseBody = _fixture('events/listEvents.401.json');
      final expected = loadFixtureObject('events/listEvents.401.json');

      expect(
        () => dataSource.getMyEvents(),
        throwsA(isA<AuthenticationException>()
            .having((e) => e.message, 'message', expected['message'])),
      );
    });
  });
}
