import 'package:equatable/equatable.dart';

class MosaicFeedItem extends Equatable {
  final String id;
  final String eventId;
  final String userId;
  final String username;
  final String displayName;
  final String? avatar;
  final FeedItemType type;
  final String? content;
  final List<String> mediaUrls;
  final DateTime? timestamp;
  final int likesCount;
  final int commentsCount;
  final bool isLiked;
  
  const MosaicFeedItem({
    required this.id,
    required this.eventId,
    required this.userId,
    required this.username,
    required this.displayName,
    this.avatar,
    required this.type,
    this.content,
    this.mediaUrls = const [],
    this.timestamp,
    this.likesCount = 0,
    this.commentsCount = 0,
    this.isLiked = false,
  });
  
  @override
  List<Object?> get props => [
        id,
        eventId,
        userId,
        username,
        displayName,
        avatar,
        type,
        content,
        mediaUrls,
        timestamp,
        likesCount,
        commentsCount,
        isLiked,
      ];
}

enum FeedItemType {
  checkIn,
  post,
  photo,
  reaction,
  story,
  liveUpdate,
  announcement,
}

class MosaicGrid extends Equatable {
  final String eventId;
  final List<List<MosaicItem?>> grid;
  final int totalItems;
  final int lastUpdatedIndex;
  final DateTime? lastUpdatedAt;
  
  const MosaicGrid({
    required this.eventId,
    required this.grid,
    this.totalItems = 0,
    this.lastUpdatedIndex = -1,
    this.lastUpdatedAt,
  });
  
  @override
  List<Object?> get props => [
        eventId,
        grid,
        totalItems,
        lastUpdatedIndex,
        lastUpdatedAt,
      ];
}

class MosaicItem extends Equatable {
  final String id;
  final String eventId;
  final String userId;
  final String username;
  final String displayName;
  final String? avatar;
  final String? content;
  final String? mediaUrl;
  final DateTime? timestamp;
  final int likesCount;
  final int commentsCount;
  
  const MosaicItem({
    required this.id,
    required this.eventId,
    required this.userId,
    required this.username,
    required this.displayName,
    this.avatar,
    this.content,
    this.mediaUrl,
    this.timestamp,
    this.likesCount = 0,
    this.commentsCount = 0,
  });
  
  @override
  List<Object?> get props => [
        id,
        eventId,
        userId,
        username,
        displayName,
        avatar,
        content,
        mediaUrl,
        timestamp,
        likesCount,
        commentsCount,
      ];
}
