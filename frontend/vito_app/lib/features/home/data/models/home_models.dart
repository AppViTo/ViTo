import 'package:equatable/equatable.dart';

class HomeUser extends Equatable {
  final String id;
  final String displayName;
  final String? avatar;
  final String? username;
  final int notificationCount;

  const HomeUser({
    required this.id,
    required this.displayName,
    this.avatar,
    this.username,
    this.notificationCount = 0,
  });

  factory HomeUser.mock() {
    return const HomeUser(
      id: 'u1',
      displayName: 'Alex',
      username: '@alex_villa',
      avatar: null,
      notificationCount: 5,
    );
  }

  @override
  List<Object?> get props => [id, displayName, avatar, username, notificationCount];
}

class HomeEvent extends Equatable {
  final String id;
  final String title;
  final String? coverImage;
  final DateTime date;
  final String? location;
  final String category;
  final bool isLive;
  final int attendeeCount;
  final List<HomeGuest> guests;

  const HomeEvent({
    required this.id,
    required this.title,
    this.coverImage,
    required this.date,
    this.location,
    this.category = 'general',
    this.isLive = false,
    this.attendeeCount = 0,
    this.guests = const [],
  });

  @override
  List<Object?> get props => [id, title, coverImage, date, location, category, isLive, attendeeCount, guests];
}

class HomeGuest extends Equatable {
  final String id;
  final String displayName;
  final String? avatar;
  final bool isOnline;

  const HomeGuest({
    required this.id,
    required this.displayName,
    this.avatar,
    this.isOnline = false,
  });

  @override
  List<Object?> get props => [id, displayName, avatar, isOnline];
}

class LiveStream extends Equatable {
  final String id;
  final String title;
  final String hostName;
  final String? hostAvatar;
  final int viewerCount;
  final String? eventTitle;
  final DateTime startedAt;

  const LiveStream({
    required this.id,
    required this.title,
    required this.hostName,
    this.hostAvatar,
    this.viewerCount = 0,
    this.eventTitle,
    required this.startedAt,
  });

  @override
  List<Object?> get props => [id, title, hostName, hostAvatar, viewerCount, eventTitle, startedAt];
}

class ActiveFriend extends Equatable {
  final String id;
  final String displayName;
  final String? avatar;
  final String? currentActivity;
  final bool isOnline;

  const ActiveFriend({
    required this.id,
    required this.displayName,
    this.avatar,
    this.currentActivity,
    this.isOnline = true,
  });

  @override
  List<Object?> get props => [id, displayName, avatar, currentActivity, isOnline];
}

class AppNotification extends Equatable {
  final String id;
  final String title;
  final String body;
  final DateTime timestamp;
  final bool isRead;
  final String? type;

  const AppNotification({
    required this.id,
    required this.title,
    required this.body,
    required this.timestamp,
    this.isRead = false,
    this.type,
  });

  @override
  List<Object?> get props => [id, title, body, timestamp, isRead, type];
}

class Story extends Equatable {
  final String id;
  final String userId;
  final String userName;
  final String? userAvatar;
  final String? imageUrl;
  final DateTime timestamp;
  final bool isViewed;

  const Story({
    required this.id,
    required this.userId,
    required this.userName,
    this.userAvatar,
    this.imageUrl,
    required this.timestamp,
    this.isViewed = false,
  });

  @override
  List<Object?> get props => [id, userId, userName, userAvatar, imageUrl, timestamp, isViewed];
}
