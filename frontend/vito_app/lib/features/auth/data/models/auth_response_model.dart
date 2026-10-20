import '../../../../core/error/exceptions.dart';
import 'user_model.dart';

/// Envelope de tokens del contrato (`schemas.AuthResponse`, ADR-004).
///
/// Lo devuelven `POST /auth/register`, `POST /auth/login` y
/// `POST /auth/refresh`. La capa de datos es quien lo traduce a
/// `UserModel` + tokens para persistirlos; el dominio solo ve `User`.
class AuthResponseModel {
  final UserModel user;
  final String accessToken;
  final String refreshToken;
  final String tokenType;
  final int? expiresIn;

  const AuthResponseModel({
    required this.user,
    required this.accessToken,
    required this.refreshToken,
    this.tokenType = 'Bearer',
    this.expiresIn,
  });

  factory AuthResponseModel.fromJson(Map<String, dynamic> json) {
    final user = json['user'];
    final accessToken = json['accessToken'];
    final refreshToken = json['refreshToken'];

    if (user is! Map<String, dynamic>) {
      throw const ServerException(message: 'AuthResponse sin campo user');
    }
    if (accessToken is! String || accessToken.isEmpty) {
      throw const ServerException(message: 'AuthResponse sin accessToken');
    }
    if (refreshToken is! String || refreshToken.isEmpty) {
      throw const ServerException(message: 'AuthResponse sin refreshToken');
    }

    return AuthResponseModel(
      user: UserModel.fromJson(user),
      accessToken: accessToken,
      refreshToken: refreshToken,
      tokenType: (json['tokenType'] as String?) ?? 'Bearer',
      expiresIn: json['expiresIn'] as int?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'accessToken': accessToken,
      'refreshToken': refreshToken,
      'tokenType': tokenType,
      'expiresIn': expiresIn,
      'user': user.toJson(),
    };
  }
}
