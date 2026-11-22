import 'package:equatable/equatable.dart';

/// Perfil social de un usuario visto por quien consulta (`SocialProfile` del
/// contrato, Ola 2a). La identidad viene de `auth-service` y los contadores
/// y booleanos del grafo de `social-service`. Nunca correo ni teléfono.
class SocialProfile extends Equatable {
  final String userId;
  final String username;
  final String? displayName;
  final String? avatar;
  final int followersCount;
  final int followingCount;

  /// Quien consulta sigue a este usuario. `false` en el propio perfil.
  final bool isFollowing;

  /// Este usuario sigue a quien consulta. `false` en el propio perfil.
  final bool isFollowedBy;

  const SocialProfile({
    required this.userId,
    required this.username,
    this.displayName,
    this.avatar,
    this.followersCount = 0,
    this.followingCount = 0,
    this.isFollowing = false,
    this.isFollowedBy = false,
  });

  /// Nombre para mostrar: el visible si lo tiene, si no el usuario.
  String get label {
    final name = displayName?.trim();
    return name != null && name.isNotEmpty ? name : username;
  }

  SocialProfile copyWith({
    int? followersCount,
    int? followingCount,
    bool? isFollowing,
    bool? isFollowedBy,
  }) {
    return SocialProfile(
      userId: userId,
      username: username,
      displayName: displayName,
      avatar: avatar,
      followersCount: followersCount ?? this.followersCount,
      followingCount: followingCount ?? this.followingCount,
      isFollowing: isFollowing ?? this.isFollowing,
      isFollowedBy: isFollowedBy ?? this.isFollowedBy,
    );
  }

  @override
  List<Object?> get props => [
        userId,
        username,
        displayName,
        avatar,
        followersCount,
        followingCount,
        isFollowing,
        isFollowedBy,
      ];
}
