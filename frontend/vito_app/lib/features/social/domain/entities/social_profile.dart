import 'package:equatable/equatable.dart';

class SocialProfile extends Equatable {
  final String userId;
  final String username;
  final String displayName;
  final String? avatar;
  final String? bio;
  final int followersCount;
  final int followingCount;
  final bool isFollowing;
  final bool isFollowedBy;
  
  const SocialProfile({
    required this.userId,
    required this.username,
    required this.displayName,
    this.avatar,
    this.bio,
    this.followersCount = 0,
    this.followingCount = 0,
    this.isFollowing = false,
    this.isFollowedBy = false,
  });
  
  SocialProfile copyWith({
    String? userId,
    String? username,
    String? displayName,
    String? avatar,
    String? bio,
    int? followersCount,
    int? followingCount,
    bool? isFollowing,
    bool? isFollowedBy,
  }) {
    return SocialProfile(
      userId: userId ?? this.userId,
      username: username ?? this.username,
      displayName: displayName ?? this.displayName,
      avatar: avatar ?? this.avatar,
      bio: bio ?? this.bio,
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
        bio,
        followersCount,
        followingCount,
        isFollowing,
        isFollowedBy,
      ];
}
