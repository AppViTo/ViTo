import '../../domain/entities/mosaic.dart';

class MosaicFeedItemModel extends MosaicFeedItem {
  const MosaicFeedItemModel({
    required super.id,
    required super.eventId,
    required super.userId,
    required super.username,
    required super.displayName,
    super.avatar,
    required super.type,
    super.content,
    super.mediaUrls,
    super.timestamp,
    super.likesCount,
    super.commentsCount,
    super.isLiked,
  });
  
  factory MosaicFeedItemModel.fromJson(Map<String, dynamic> json) {
    return MosaicFeedItemModel(
      id: json['id'] ?? '',
      eventId: json['eventId'] ?? '',
      userId: json['userId'] ?? '',
      username: json['username'] ?? '',
      displayName: json['displayName'] ?? '',
      avatar: json['avatar'],
      type: _parseType(json['type']),
      content: json['content'],
      mediaUrls: json['mediaUrls'] != null 
          ? List<String>.from(json['mediaUrls']) 
          : [],
      timestamp: json['timestamp'] != null 
          ? DateTime.parse(json['timestamp']) 
          : null,
      likesCount: json['likesCount'] ?? 0,
      commentsCount: json['commentsCount'] ?? 0,
      isLiked: json['isLiked'] ?? false,
    );
  }
  
  static FeedItemType _parseType(String? type) {
    switch (type) {
      case 'CHECK_IN':
        return FeedItemType.checkIn;
      case 'POST':
        return FeedItemType.post;
      case 'PHOTO':
        return FeedItemType.photo;
      case 'REACTION':
        return FeedItemType.reaction;
      case 'STORY':
        return FeedItemType.story;
      case 'LIVE_UPDATE':
        return FeedItemType.liveUpdate;
      case 'ANNOUNCEMENT':
        return FeedItemType.announcement;
      default:
        return FeedItemType.post;
    }
  }
  
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'eventId': eventId,
      'userId': userId,
      'username': username,
      'displayName': displayName,
      'avatar': avatar,
      'type': type.name.toUpperCase(),
      'content': content,
      'mediaUrls': mediaUrls,
      'timestamp': timestamp?.toIso8601String(),
      'likesCount': likesCount,
      'commentsCount': commentsCount,
      'isLiked': isLiked,
    };
  }
}

class MosaicGridModel extends MosaicGrid {
  const MosaicGridModel({
    required super.eventId,
    required super.grid,
    super.totalItems,
    super.lastUpdatedIndex,
    super.lastUpdatedAt,
  });
  
  factory MosaicGridModel.fromJson(Map<String, dynamic> json) {
    List<List<MosaicItem?>> grid = [];
    
    if (json['grid'] != null) {
      for (var row in json['grid']) {
        List<MosaicItem?> gridRow = [];
        for (var item in row) {
          gridRow.add(item != null ? MosaicItemModel.fromJson(item) : null);
        }
        grid.add(gridRow);
      }
    }
    
    return MosaicGridModel(
      eventId: json['eventId'] ?? '',
      grid: grid,
      totalItems: json['totalItems'] ?? 0,
      lastUpdatedIndex: json['lastUpdatedIndex'] ?? -1,
      lastUpdatedAt: json['lastUpdatedAt'] != null 
          ? DateTime.parse(json['lastUpdatedAt']) 
          : null,
    );
  }
  
  Map<String, dynamic> toJson() {
    return {
      'eventId': eventId,
      'grid': grid.map((row) => row.map((item) => 
          item != null ? (item as MosaicItemModel).toJson() : null).toList()).toList(),
      'totalItems': totalItems,
      'lastUpdatedIndex': lastUpdatedIndex,
      'lastUpdatedAt': lastUpdatedAt?.toIso8601String(),
    };
  }
}

class MosaicItemModel extends MosaicItem {
  const MosaicItemModel({
    required super.id,
    required super.eventId,
    required super.userId,
    required super.username,
    required super.displayName,
    super.avatar,
    super.content,
    super.mediaUrl,
    super.timestamp,
    super.likesCount,
    super.commentsCount,
  });
  
  factory MosaicItemModel.fromJson(Map<String, dynamic> json) {
    return MosaicItemModel(
      id: json['id'] ?? '',
      eventId: json['eventId'] ?? '',
      userId: json['userId'] ?? '',
      username: json['username'] ?? '',
      displayName: json['displayName'] ?? '',
      avatar: json['avatar'],
      content: json['content'],
      mediaUrl: json['mediaUrl'],
      timestamp: json['timestamp'] != null 
          ? DateTime.parse(json['timestamp']) 
          : null,
      likesCount: json['likesCount'] ?? 0,
      commentsCount: json['commentsCount'] ?? 0,
    );
  }
  
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'eventId': eventId,
      'userId': userId,
      'username': username,
      'displayName': displayName,
      'avatar': avatar,
      'content': content,
      'mediaUrl': mediaUrl,
      'timestamp': timestamp?.toIso8601String(),
      'likesCount': likesCount,
      'commentsCount': commentsCount,
    };
  }
}
