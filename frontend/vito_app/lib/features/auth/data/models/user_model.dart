import '../../domain/entities/user.dart';

class UserModel extends User {
  const UserModel({
    required super.id,
    required super.username,
    required super.email,
    super.profile,
    super.stats,
    super.roles,
    super.createdAt,
  });
  
  factory UserModel.fromJson(Map<String, dynamic> json) {
    return UserModel(
      id: json['id'] ?? '',
      username: json['username'] ?? '',
      email: json['email'] ?? '',
      profile: json['profile'] != null 
          ? UserProfileModel.fromJson(json['profile']) 
          : null,
      stats: json['stats'] != null 
          ? UserStatsModel.fromJson(json['stats']) 
          : null,
      roles: json['roles'] != null 
          ? List<String>.from(json['roles']) 
          : null,
      createdAt: json['createdAt'] != null 
          ? DateTime.parse(json['createdAt']) 
          : null,
    );
  }
  
  /// `AuthUser` plano de register/login/refresh (ADR-004):
  /// `{id, username, email, displayName, avatar}`. `displayName` y `avatar`
  /// se guardan en [profile] para que la UI los lea igual que en social.
  factory UserModel.fromAuthJson(Map<String, dynamic> json) {
    final displayName = json['displayName'] as String?;
    final avatar = json['avatar'] as String?;
    return UserModel(
      id: json['id'] ?? '',
      username: json['username'] ?? '',
      email: json['email'] ?? '',
      profile: displayName != null || avatar != null
          ? UserProfileModel(displayName: displayName, avatar: avatar)
          : null,
    );
  }

  Map<String, dynamic> toAuthJson() {
    return {
      'id': id,
      'username': username,
      'email': email,
      'displayName': profile?.displayName,
      'avatar': profile?.avatar,
    };
  }

  @override
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'username': username,
      'email': email,
      'profile': profile != null ? (profile as UserProfileModel).toJson() : null,
      'stats': stats != null ? (stats as UserStatsModel).toJson() : null,
      'roles': roles,
      'createdAt': createdAt?.toIso8601String(),
    };
  }
}

class UserProfileModel extends UserProfile {
  const UserProfileModel({
    super.displayName,
    super.bio,
    super.avatar,
    super.coverImage,
    super.location,
  });
  
  factory UserProfileModel.fromJson(Map<String, dynamic> json) {
    return UserProfileModel(
      displayName: json['displayName'],
      bio: json['bio'],
      avatar: json['avatar'],
      coverImage: json['coverImage'],
      location: json['location'] != null 
          ? UserLocationModel.fromJson(json['location']) 
          : null,
    );
  }
  
  @override
  Map<String, dynamic> toJson() {
    return {
      'displayName': displayName,
      'bio': bio,
      'avatar': avatar,
      'coverImage': coverImage,
      'location': location != null ? (location as UserLocationModel).toJson() : null,
    };
  }
}

class UserLocationModel extends UserLocation {
  const UserLocationModel({
    super.type,
    super.coordinates,
    super.city,
    super.country,
  });
  
  factory UserLocationModel.fromJson(Map<String, dynamic> json) {
    return UserLocationModel(
      type: json['type'],
      coordinates: json['coordinates'] != null 
          ? List<double>.from(json['coordinates']) 
          : null,
      city: json['city'],
      country: json['country'],
    );
  }
  
  @override
  Map<String, dynamic> toJson() {
    return {
      'type': type,
      'coordinates': coordinates,
      'city': city,
      'country': country,
    };
  }
}

class UserStatsModel extends UserStats {
  const UserStatsModel({
    super.followersCount,
    super.followingCount,
    super.postsCount,
    super.streamsCount,
  });
  
  factory UserStatsModel.fromJson(Map<String, dynamic> json) {
    return UserStatsModel(
      followersCount: json['followersCount'] ?? 0,
      followingCount: json['followingCount'] ?? 0,
      postsCount: json['postsCount'] ?? 0,
      streamsCount: json['streamsCount'] ?? 0,
    );
  }
  
  @override
  Map<String, dynamic> toJson() {
    return {
      'followersCount': followersCount,
      'followingCount': followingCount,
      'postsCount': postsCount,
      'streamsCount': streamsCount,
    };
  }
}
