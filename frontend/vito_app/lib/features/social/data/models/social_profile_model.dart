import '../../domain/entities/social_profile.dart';

class SocialProfileModel extends SocialProfile {
  const SocialProfileModel({
    required super.userId,
    required super.username,
    required super.displayName,
    super.avatar,
    super.bio,
    super.followersCount,
    super.followingCount,
    super.isFollowing,
    super.isFollowedBy,
  });
  
  factory SocialProfileModel.fromJson(Map<String, dynamic> json) {
    return SocialProfileModel(
      userId: json['userId'] ?? '',
      username: json['username'] ?? '',
      displayName: json['displayName'] ?? '',
      avatar: json['avatar'],
      bio: json['bio'],
      followersCount: json['followersCount'] ?? 0,
      followingCount: json['followingCount'] ?? 0,
      isFollowing: json['isFollowing'] ?? false,
      isFollowedBy: json['isFollowedBy'] ?? false,
    );
  }
  
  Map<String, dynamic> toJson() {
    return {
      'userId': userId,
      'username': username,
      'displayName': displayName,
      'avatar': avatar,
      'bio': bio,
      'followersCount': followersCount,
      'followingCount': followingCount,
      'isFollowing': isFollowing,
      'isFollowedBy': isFollowedBy,
    };
  }
}
