import 'package:equatable/equatable.dart';

class User extends Equatable {
  final String id;
  final String username;
  final String email;
  final UserProfile? profile;
  final UserStats? stats;
  final List<String>? roles;
  final DateTime? createdAt;
  
  const User({
    required this.id,
    required this.username,
    required this.email,
    this.profile,
    this.stats,
    this.roles,
    this.createdAt,
  });
  
  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: json['id'] ?? '',
      username: json['username'] ?? '',
      email: json['email'] ?? '',
      profile: json['profile'] != null 
          ? UserProfile.fromJson(json['profile']) 
          : null,
      stats: json['stats'] != null 
          ? UserStats.fromJson(json['stats']) 
          : null,
      roles: json['roles'] != null 
          ? List<String>.from(json['roles']) 
          : null,
      createdAt: json['createdAt'] != null 
          ? DateTime.parse(json['createdAt']) 
          : null,
    );
  }
  
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'username': username,
      'email': email,
      'profile': profile?.toJson(),
      'stats': stats?.toJson(),
      'roles': roles,
      'createdAt': createdAt?.toIso8601String(),
    };
  }
  
  @override
  List<Object?> get props => [id, username, email, profile, stats, roles, createdAt];
}

class UserProfile extends Equatable {
  final String? displayName;
  final String? bio;
  final String? avatar;
  final String? coverImage;
  final UserLocation? location;
  
  const UserProfile({
    this.displayName,
    this.bio,
    this.avatar,
    this.coverImage,
    this.location,
  });
  
  factory UserProfile.fromJson(Map<String, dynamic> json) {
    return UserProfile(
      displayName: json['displayName'],
      bio: json['bio'],
      avatar: json['avatar'],
      coverImage: json['coverImage'],
      location: json['location'] != null 
          ? UserLocation.fromJson(json['location']) 
          : null,
    );
  }
  
  Map<String, dynamic> toJson() {
    return {
      'displayName': displayName,
      'bio': bio,
      'avatar': avatar,
      'coverImage': coverImage,
      'location': location?.toJson(),
    };
  }
  
  @override
  List<Object?> get props => [displayName, bio, avatar, coverImage, location];
}

class UserLocation extends Equatable {
  final String? type;
  final List<double>? coordinates;
  final String? city;
  final String? country;
  
  const UserLocation({
    this.type,
    this.coordinates,
    this.city,
    this.country,
  });
  
  factory UserLocation.fromJson(Map<String, dynamic> json) {
    return UserLocation(
      type: json['type'],
      coordinates: json['coordinates'] != null 
          ? List<double>.from(json['coordinates']) 
          : null,
      city: json['city'],
      country: json['country'],
    );
  }
  
  Map<String, dynamic> toJson() {
    return {
      'type': type,
      'coordinates': coordinates,
      'city': city,
      'country': country,
    };
  }
  
  @override
  List<Object?> get props => [type, coordinates, city, country];
}

class UserStats extends Equatable {
  final int followersCount;
  final int followingCount;
  final int postsCount;
  final int streamsCount;
  
  const UserStats({
    this.followersCount = 0,
    this.followingCount = 0,
    this.postsCount = 0,
    this.streamsCount = 0,
  });
  
  factory UserStats.fromJson(Map<String, dynamic> json) {
    return UserStats(
      followersCount: json['followersCount'] ?? 0,
      followingCount: json['followingCount'] ?? 0,
      postsCount: json['postsCount'] ?? 0,
      streamsCount: json['streamsCount'] ?? 0,
    );
  }
  
  Map<String, dynamic> toJson() {
    return {
      'followersCount': followersCount,
      'followingCount': followingCount,
      'postsCount': postsCount,
      'streamsCount': streamsCount,
    };
  }
  
  @override
  List<Object?> get props => [followersCount, followingCount, postsCount, streamsCount];
}
