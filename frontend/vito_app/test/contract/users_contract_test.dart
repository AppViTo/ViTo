import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vito_app/core/error/exceptions.dart';
import 'package:vito_app/features/social/data/datasources/user_remote_datasource.dart';
import 'package:vito_app/features/social/data/models/user_summary_model.dart';
import 'package:vito_app/features/social/domain/usecases/search_users.dart';

import 'fixture_loader.dart';

String fixtureText(String relative) =>
    File(fixturePath(relative)).readAsStringSync();

/// Sirve [body] con [status] para cualquier petición y guarda la última.
class _OneShotAdapter implements HttpClientAdapter {
  final int status;
  final String body;
  RequestOptions? last;

  _OneShotAdapter(this.status, this.body);

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<List<int>>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    last = options;
    return ResponseBody.fromString(body, status, headers: {
      Headers.contentTypeHeader: ['application/json'],
    });
  }

  @override
  void close({bool force = false}) {}
}

/// T012 (spec 004, Ola 2a) — `UserSummary` y `searchUsers` contra
/// `docs/api/fixtures/users/`.
void main() {
  group('UserSummary', () {
    test('searchUsers.matches.200.json: array plano, nulos permitidos', () {
      final users = loadFixtureList('users/searchUsers.matches.200.json')
          .map((e) => UserSummaryModel.fromJson(e as Map<String, dynamic>))
          .toList();

      expect(users, hasLength(2));
      expect(users.first.userId, 'usr_01H9ZP5RKM');
      expect(users.first.username, 'andrea.gomez');
      expect(users.first.displayName, 'Andrea Gómez');
      expect(users.first.label, 'Andrea Gómez');
      expect(users.last.displayName, isNull);
      expect(users.last.avatar, isNull);
      // Sin nombre visible se muestra el usuario.
      expect(users.last.label, 'andres.r');
    });

    test('ningún fixture de usuarios trae correo ni teléfono', () {
      for (final path in const [
        'users/searchUsers.matches.200.json',
        'users/getUsersByIds.200.json',
      ]) {
        for (final raw in loadFixtureList(path).cast<Map<String, dynamic>>()) {
          expect(raw.containsKey('email'), isFalse, reason: path);
          expect(raw.containsKey('phone'), isFalse, reason: path);
          expect(UserSummaryModel.fromJson(raw).toJson().keys.toSet(),
              raw.keys.toSet());
        }
      }
    });

    test('searchUsers.empty.200.json es una lista vacía', () {
      expect(loadFixtureList('users/searchUsers.empty.200.json'), isEmpty);
    });
  });

  group('searchUsers (T034)', () {
    UserRemoteDataSourceImpl dataSourceFor(_OneShotAdapter adapter) =>
        UserRemoteDataSourceImpl(
          dio: Dio(BaseOptions(baseUrl: 'http://localhost:4010/api/v1'))
            ..httpClientAdapter = adapter,
        );

    test('llama a GET /users/search con query, page y size', () async {
      final adapter = _OneShotAdapter(
        200,
        fixtureText('users/searchUsers.matches.200.json'),
      );
      final users = await dataSourceFor(adapter).searchUsers(query: 'and');

      expect(adapter.last!.path, '/users/search');
      expect(adapter.last!.queryParameters,
          {'query': 'and', 'page': 0, 'size': 20});
      expect(users.map((u) => u.username), ['andrea.gomez', 'andres.r']);
    });

    test('el 429 se traduce en "Espera un momento y vuelve a intentarlo"',
        () async {
      final adapter =
          _OneShotAdapter(429, fixtureText('users/searchUsers.429.json'));

      expect(
        () => dataSourceFor(adapter).searchUsers(query: 'and'),
        throwsA(isA<ServerException>()
            .having((e) => e.message, 'message', searchRateLimitedMessage)
            .having((e) => e.statusCode, 'statusCode', 429)),
      );
    });

    test('el 400 trae el mensaje del backend', () async {
      final adapter =
          _OneShotAdapter(400, fixtureText('users/searchUsers.400.json'));

      expect(
        () => dataSourceFor(adapter).searchUsers(query: 'a'),
        throwsA(isA<ValidationException>().having((e) => e.message, 'message',
            'La búsqueda debe tener entre 2 y 50 caracteres')),
      );
    });
  });

  group('normalizeSearchQuery', () {
    test('quita espacios y una @ inicial', () {
      expect(normalizeSearchQuery('  @caro.mz '), 'caro.mz');
      expect(normalizeSearchQuery('José'), 'José');
      expect(normalizeSearchQuery('@'), '');
    });
  });
}
