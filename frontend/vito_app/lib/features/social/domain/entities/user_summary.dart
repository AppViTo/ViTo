import 'package:equatable/equatable.dart';

/// Datos públicos de un usuario (`UserSummary` del contrato, Ola 2a). Nunca
/// trae correo ni teléfono. Lo devuelven `searchUsers`, `getUsersByIds`,
/// `listFollowers` y `listFollowing`.
class UserSummary extends Equatable {
  final String userId;
  final String username;
  final String? displayName;
  final String? avatar;

  const UserSummary({
    required this.userId,
    required this.username,
    this.displayName,
    this.avatar,
  });

  /// Nombre para mostrar: el visible si lo tiene, si no el usuario.
  String get label {
    final name = displayName?.trim();
    return name != null && name.isNotEmpty ? name : username;
  }

  @override
  List<Object?> get props => [userId, username, displayName, avatar];
}
