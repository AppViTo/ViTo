import 'package:flutter/widgets.dart';

/// Enlace de invitación que alguien abrió sin sesión (T043).
///
/// La página `/i/<token>` lo guarda antes de ir al inicio de sesión o al
/// registro; al autenticarse, [goAfterAuth] vuelve a la invitación en lugar
/// del inicio. Vive en memoria: si la web se recarga en medio, se pierde y la
/// persona vuelve a abrir el enlace.
class PendingInviteLink {
  PendingInviteLink._();

  static String? _token;

  static void remember(String token) => _token = token;

  /// Devuelve el token pendiente y lo olvida.
  static String? take() {
    final token = _token;
    _token = null;
    return token;
  }

  static void clear() => _token = null;
}

/// Ruta de la página de invitación de [token].
String inviteLinkRoute(String token) => '/i/$token';

/// Tras iniciar sesión o registrarse: a la invitación pendiente, si la hay,
/// o al inicio.
void goAfterAuth(BuildContext context) {
  final token = PendingInviteLink.take();
  if (token == null) {
    Navigator.pushReplacementNamed(context, '/home');
    return;
  }
  Navigator.pushNamedAndRemoveUntil(
    context,
    inviteLinkRoute(token),
    (_) => false,
  );
}
