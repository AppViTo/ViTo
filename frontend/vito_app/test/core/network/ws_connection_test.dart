import 'dart:async';

import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/core/network/ws_connection.dart';

class FakeSocket implements WsSocket {
  final controller = StreamController<dynamic>();
  final sent = <dynamic>[];
  Uri? uri;
  int? _closeCode;
  bool closed = false;

  @override
  Stream<dynamic> get stream => controller.stream;

  @override
  void add(dynamic data) => sent.add(data);

  @override
  Future<void> close([int? code, String? reason]) async {
    closed = true;
    _closeCode = code;
  }

  @override
  int? get closeCode => _closeCode;

  Future<void> closeWith(int code) async {
    _closeCode = code;
    await controller.close();
  }
}

void main() {
  group('wsUriForPath', () {
    test('usa el host de WS_BASE_URL y agrega ?token=', () {
      final uri = wsUriForPath('/ws/checkin/evt_1', 'access-123');

      expect(uri.toString(),
          'ws://localhost:8080/ws/checkin/evt_1?token=access-123');
      expect(uri.queryParameters['token'], 'access-123');
    });

    test('el chat no envía userId (ADR-015)', () {
      final uri = wsUriForPath('/ws/chat', 'access-123');

      expect(uri.path, '/ws/chat');
      expect(uri.queryParameters, {'token': 'access-123'});
    });
  });

  group('WsConnection', () {
    late List<FakeSocket> sockets;
    late List<dynamic> received;

    FakeSocket factory(Uri uri) {
      final socket = FakeSocket()..uri = uri;
      sockets.add(socket);
      return socket;
    }

    setUp(() {
      sockets = [];
      received = [];
    });

    test('conecta con el access token actual y reenvía los frames',
        () async {
      final connection = WsConnection(
        tokenProvider: () async => 'token-1',
        refreshAccessToken: () async => null,
        socketFactory: factory,
      );

      await connection.connect('/ws/checkin/evt_1', onData: received.add);

      expect(sockets, hasLength(1));
      expect(sockets.single.uri!.path, '/ws/checkin/evt_1');
      expect(sockets.single.uri!.queryParameters, {'token': 'token-1'});

      sockets.single.controller.add({'type': 'CHECK_IN'});
      await pumpEventQueue();

      expect(received, [
        {'type': 'CHECK_IN'}
      ]);
    });

    test('sin access token no abre la conexión', () async {
      final connection = WsConnection(
        tokenProvider: () async => null,
        refreshAccessToken: () async => null,
        socketFactory: factory,
      );

      await connection.connect('/ws/mosaic/evt_1', onData: received.add);

      expect(sockets, isEmpty);
    });

    test('cierre 4401: refresca y reconecta una sola vez', () async {
      var refreshCalls = 0;
      var token = 'viejo';

      final connection = WsConnection(
        tokenProvider: () async => token,
        refreshAccessToken: () async {
          refreshCalls++;
          token = 'nuevo';
          return token;
        },
        socketFactory: factory,
      );

      await connection.connect('/ws/mosaic/evt_1', onData: received.add);
      expect(sockets.single.uri!.queryParameters['token'], 'viejo');

      await sockets.single.closeWith(
        WsConnection.unauthorizedCloseCode,
      );
      await pumpEventQueue();

      expect(refreshCalls, 1);
      expect(sockets, hasLength(2));
      expect(sockets.last.uri!.queryParameters['token'], 'nuevo');

      await sockets.last.closeWith(WsConnection.unauthorizedCloseCode);
      await pumpEventQueue();

      expect(refreshCalls, 1);
      expect(sockets, hasLength(2));
    });

    test('otros cierres no disparan refresh', () async {
      var refreshCalls = 0;

      final connection = WsConnection(
        tokenProvider: () async => 'token-1',
        refreshAccessToken: () async {
          refreshCalls++;
          return 'token-2';
        },
        socketFactory: factory,
      );

      await connection.connect('/ws/streaming/stream_1', onData: received.add);
      await sockets.single.closeWith(1000);
      await pumpEventQueue();

      expect(refreshCalls, 0);
      expect(sockets, hasLength(1));
    });

    test('si el refresh falla no reconecta', () async {
      var refreshCalls = 0;

      final connection = WsConnection(
        tokenProvider: () async => 'token-1',
        refreshAccessToken: () async {
          refreshCalls++;
          return null;
        },
        socketFactory: factory,
      );

      await connection.connect('/ws/streaming/stream_1', onData: received.add);
      await sockets.single.closeWith(
        WsConnection.unauthorizedCloseCode,
      );
      await pumpEventQueue();

      expect(refreshCalls, 1);
      expect(sockets, hasLength(1));
    });
  });
}
