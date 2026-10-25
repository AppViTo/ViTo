import 'user_model.dart';

/// `AuthResponse` de register, login y refresh (ADR-004).
///
/// Es el único parser de esa respuesta: lo usan el datasource y el
/// `TokenInterceptor`, para que los dos lean exactamente los mismos campos.
class AuthResponseModel {
  final String accessToken;
  final String refreshToken;

  /// Segundos hasta que expira el access token.
  final int expiresIn;
  final UserModel user;

  const AuthResponseModel({
    required this.accessToken,
    required this.refreshToken,
    required this.expiresIn,
    required this.user,
  });

  factory AuthResponseModel.fromJson(Map<String, dynamic> json) {
    return AuthResponseModel(
      accessToken: json['accessToken'] as String,
      refreshToken: json['refreshToken'] as String,
      expiresIn: json['expiresIn'] as int,
      user: UserModel.fromAuthJson(json['user'] as Map<String, dynamic>),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'accessToken': accessToken,
      'refreshToken': refreshToken,
      'expiresIn': expiresIn,
      'user': user.toAuthJson(),
    };
  }
}
