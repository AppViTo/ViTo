import 'dart:async';

import 'package:web_socket_channel/web_socket_channel.dart';

import '../../config/env/env.dart';

/// Abstracción mínima del socket para poder probar cierres y reconexiones sin
/// abrir una conexión real.
abstract class WsSocket {
  Stream<dynamic> get stream;
  void add(dynamic data);
  Future<void> close([int? code, String? reason]);
  int? get closeCode;
}

class WebSocketChannelSocket implements WsSocket {
  final WebSocketChannel channel;

  WebSocketChannelSocket(this.channel);

  @override
  Stream<dynamic> get stream => channel.stream;

  @override
  void add(dynamic data) => channel.sink.add(data);

  @override
  Future<void> close([int? code, String? reason]) =>
      channel.sink.close(code, reason);

  @override
  int? get closeCode => channel.closeCode;
}

typedef WsSocketFactory = WsSocket Function(Uri uri);
typedef AccessTokenProvider = Future<String?> Function();
typedef AccessTokenRefresher = Future<String?> Function();

WsSocket connectWebSocket(Uri uri) =>
    WebSocketChannelSocket(WebSocketChannel.connect(uri));

/// URL del canal WS a través del gateway con `?token=` (CONVENTIONS §7,
/// ADR-006). `path` empieza por `/ws/...`.
Uri wsUriForPath(String path, String token) {
  final base = Env.wsBaseUrl.replaceFirst(RegExp(r'/+$'), '');
  final uri = Uri.parse('$base$path');
  return uri.replace(
    queryParameters: {...uri.queryParameters, 'token': token},
  );
}

/// Conexión WS cruda con autenticación por query y renovación única ante el
/// cierre `4401` (ADR-006): refresca el access token, reconecta una sola vez.
class WsConnection {
  static const unauthorizedCloseCode = 4401;

  final AccessTokenProvider tokenProvider;
  final AccessTokenRefresher refreshAccessToken;
  final WsSocketFactory socketFactory;

  WsSocket? _socket;
  StreamSubscription<dynamic>? _subscription;
  String? _path;
  void Function(dynamic data)? _onData;
  void Function(Object error)? _onError;
  bool _allowReconnect = false;
  int _generation = 0;

  WsConnection({
    required this.tokenProvider,
    required this.refreshAccessToken,
    this.socketFactory = connectWebSocket,
  });

  Future<void> connect(
    String path, {
    required void Function(dynamic data) onData,
    void Function(Object error)? onError,
  }) async {
    await disconnect();

    _path = path;
    _onData = onData;
    _onError = onError;
    _allowReconnect = true;

    final generation = _generation;
    final token = await tokenProvider();
    if (generation != _generation || token == null || token.isEmpty) {
      return;
    }

    _open(wsUriForPath(path, token));
  }

  void send(dynamic data) => _socket?.add(data);

  Future<void> disconnect() async {
    _generation++;
    _allowReconnect = false;
    _path = null;
    _onData = null;
    _onError = null;

    final subscription = _subscription;
    _subscription = null;
    final socket = _socket;
    _socket = null;

    await subscription?.cancel();
    await socket?.close();
  }

  void _open(Uri uri) {
    final generation = _generation;
    final socket = socketFactory(uri);
    _socket = socket;

    _subscription = socket.stream.listen(
      (data) {
        if (generation == _generation) {
          _onData?.call(data);
        }
      },
      onError: (Object error) {
        if (generation != _generation) {
          return;
        }
        _onError?.call(error);
        if (_isUnauthorized(socket, error)) {
          unawaited(_renewAndReconnect());
        }
      },
      onDone: () {
        if (generation != _generation) {
          return;
        }
        if (_isUnauthorized(socket, null)) {
          unawaited(_renewAndReconnect());
        }
      },
    );
  }

  bool _isUnauthorized(WsSocket socket, Object? error) {
    if (socket.closeCode == unauthorizedCloseCode) {
      return true;
    }
    return error?.toString().contains('$unauthorizedCloseCode') ?? false;
  }

  Future<void> _renewAndReconnect() async {
    if (!_allowReconnect) {
      return;
    }
    _allowReconnect = false;

    final path = _path;
    final generation = _generation;
    if (path == null) {
      return;
    }

    final newToken = await refreshAccessToken();
    if (generation != _generation || newToken == null || newToken.isEmpty) {
      return;
    }

    _open(wsUriForPath(path, newToken));
  }
}
