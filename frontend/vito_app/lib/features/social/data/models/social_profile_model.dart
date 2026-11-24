import '../../domain/entities/social_profile.dart';

/// `SocialProfile` del contrato (Ola 2a, T011): `userId`, `username`,
/// `displayName` y `avatar` (nullables), contadores e `isFollowing` /
/// `isFollowedBy` con esos nombres. El contrato no tiene `bio`.
class SocialProfileModel extends SocialProfile {
  const SocialProfileModel({
    required super.userId,
    required super.username,
    super.displayName,
    super.avatar,
    super.followersCount,
    super.followingCount,
    super.isFollowing,
    super.isFollowedBy,
  });

  factory SocialProfileModel.fromJson(Map<String, dynamic> json) {
    return SocialProfileModel(
      userId: json['userId'] as String,
      username: json['username'] as String,
      displayName: json['displayName'] as String?,
      avatar: json['avatar'] as String?,
      followersCount: (json['followersCount'] as int?) ?? 0,
      followingCount: (json['followingCount'] as int?) ?? 0,
      isFollowing: (json['isFollowing'] as bool?) ?? false,
      isFollowedBy: (json['isFollowedBy'] as bool?) ?? false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'userId': userId,
      'username': username,
      'displayName': displayName,
      'avatar': avatar,
      'followersCount': followersCount,
      'followingCount': followingCount,
      'isFollowing': isFollowing,
      'isFollowedBy': isFollowedBy,
    };
  }
}
