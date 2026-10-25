import 'dart:async';

/// Avisa que el refresh falló y la sesión local se limpió (ADR-004:
/// "refresh falla → limpiar tokens → redirigir a /login").
///
/// Lo emite el `TokenInterceptor` y lo escucha el `AuthBloc`, sin que la capa
/// de red dependa de la presentación.
class SessionExpiredNotifier {
  final _controller = StreamController<void>.broadcast();

  Stream<void> get stream => _controller.stream;

  void notify() => _controller.add(null);

  Future<void> dispose() => _controller.close();
}
