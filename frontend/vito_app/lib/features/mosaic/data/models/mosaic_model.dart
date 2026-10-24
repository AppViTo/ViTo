import '../../domain/entities/mosaic.dart';

/// `MosaicFeedItem` del contrato (openapi.yaml, Ola 3). `displayName` y
/// `avatar` pueden llegar `null`; si falta `displayName` se muestra el
/// `username`.
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
    final username = json['username'] as String? ?? '';
    return MosaicFeedItemModel(
      id: json['id'] as String? ?? '',
      eventId: json['eventId'] as String? ?? '',
      userId: json['userId'] as String? ?? '',
      username: username,
      displayName: json['displayName'] as String? ?? username,
      avatar: json['avatar'] as String?,
      type: _parseType(json['type'] as String?),
      content: json['content'] as String?,
      mediaUrls: json['mediaUrls'] is List
          ? List<String>.from(json['mediaUrls'] as List)
          : const [],
      timestamp: json['timestamp'] is String
          ? DateTime.parse(json['timestamp'] as String)
          : null,
      likesCount: json['likesCount'] as int? ?? 0,
      commentsCount: json['commentsCount'] as int? ?? 0,
      isLiked: json['isLiked'] as bool? ?? false,
    );
  }

  static const Map<String, FeedItemType> _types = {
    'CHECK_IN': FeedItemType.checkIn,
    'POST': FeedItemType.post,
    'PHOTO': FeedItemType.photo,
    'REACTION': FeedItemType.reaction,
    'STORY': FeedItemType.story,
    'LIVE_UPDATE': FeedItemType.liveUpdate,
    'ANNOUNCEMENT': FeedItemType.announcement,
  };

  static FeedItemType _parseType(String? type) =>
      _types[type] ?? FeedItemType.post;

  static String _typeToJson(FeedItemType type) =>
      _types.entries.firstWhere((entry) => entry.value == type).key;

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'eventId': eventId,
      'userId': userId,
      'username': username,
      'displayName': displayName,
      'avatar': avatar,
      'type': _typeToJson(type),
      'content': content,
      'mediaUrls': mediaUrls,
      'timestamp': timestamp?.toIso8601String(),
      'likesCount': likesCount,
      'commentsCount': commentsCount,
      'isLiked': isLiked,
    };
  }
}

/// `MosaicGrid` del contrato: `grid[fila][columna]` con `null` en las celdas
/// vacías.
class MosaicGridModel extends MosaicGrid {
  const MosaicGridModel({
    required super.eventId,
    required super.grid,
    super.totalItems,
    super.lastUpdatedIndex,
    super.lastUpdatedAt,
  });

  factory MosaicGridModel.fromJson(Map<String, dynamic> json) {
    final grid = <List<MosaicItem?>>[];

    if (json['grid'] is List) {
      for (final row in json['grid'] as List) {
        final gridRow = <MosaicItem?>[];
        for (final item in row as List) {
          gridRow.add(item is Map<String, dynamic>
              ? MosaicItemModel.fromJson(item)
              : null);
        }
        grid.add(gridRow);
      }
    }

    return MosaicGridModel(
      eventId: json['eventId'] as String? ?? '',
      grid: grid,
      totalItems: json['totalItems'] as int? ?? 0,
      lastUpdatedIndex: json['lastUpdatedIndex'] as int? ?? -1,
      lastUpdatedAt: json['lastUpdatedAt'] is String
          ? DateTime.parse(json['lastUpdatedAt'] as String)
          : null,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'eventId': eventId,
      'grid': grid
          .map((row) => row
              .map((item) => item != null
                  ? MosaicItemModel.fromEntity(item).toJson()
                  : null)
              .toList())
          .toList(),
      'totalItems': totalItems,
      'lastUpdatedIndex': lastUpdatedIndex,
      'lastUpdatedAt': lastUpdatedAt?.toIso8601String(),
    };
  }
}

/// `MosaicItem` del contrato: celda ocupada de la grilla, con `type` y
/// `position`.
class MosaicItemModel extends MosaicItem {
  const MosaicItemModel({
    required super.id,
    required super.eventId,
    required super.userId,
    required super.username,
    required super.displayName,
    super.avatar,
    super.type,
    super.content,
    super.mediaUrl,
    super.timestamp,
    super.position,
    super.likesCount,
    super.commentsCount,
  });

  factory MosaicItemModel.fromEntity(MosaicItem item) {
    if (item is MosaicItemModel) return item;
    return MosaicItemModel(
      id: item.id,
      eventId: item.eventId,
      userId: item.userId,
      username: item.username,
      displayName: item.displayName,
      avatar: item.avatar,
      type: item.type,
      content: item.content,
      mediaUrl: item.mediaUrl,
      timestamp: item.timestamp,
      position: item.position,
      likesCount: item.likesCount,
      commentsCount: item.commentsCount,
    );
  }

  factory MosaicItemModel.fromJson(Map<String, dynamic> json) {
    final username = json['username'] as String? ?? '';
    final position = json['position'];
    return MosaicItemModel(
      id: json['id'] as String? ?? '',
      eventId: json['eventId'] as String? ?? '',
      userId: json['userId'] as String? ?? '',
      username: username,
      displayName: json['displayName'] as String? ?? username,
      avatar: json['avatar'] as String?,
      type: _parseType(json['type'] as String?),
      content: json['content'] as String?,
      mediaUrl: json['mediaUrl'] as String?,
      timestamp: json['timestamp'] is String
          ? DateTime.parse(json['timestamp'] as String)
          : null,
      position: position is Map<String, dynamic>
          ? MosaicItemPosition(
              row: position['row'] as int? ?? 0,
              col: position['col'] as int? ?? 0,
              width: position['width'] as int? ?? 1,
              height: position['height'] as int? ?? 1,
            )
          : const MosaicItemPosition(row: 0, col: 0),
      likesCount: json['likesCount'] as int? ?? 0,
      commentsCount: json['commentsCount'] as int? ?? 0,
    );
  }

  static const Map<String, MosaicItemType> _types = {
    'CHECK_IN': MosaicItemType.checkIn,
    'POST': MosaicItemType.post,
    'PHOTO': MosaicItemType.photo,
    'REACTION': MosaicItemType.reaction,
    'STORY': MosaicItemType.story,
    'HIGHLIGHT': MosaicItemType.highlight,
  };

  static MosaicItemType _parseType(String? type) =>
      _types[type] ?? MosaicItemType.photo;

  static String _typeToJson(MosaicItemType type) =>
      _types.entries.firstWhere((entry) => entry.value == type).key;

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'eventId': eventId,
      'userId': userId,
      'username': username,
      'displayName': displayName,
      'avatar': avatar,
      'type': _typeToJson(type),
      'content': content,
      'mediaUrl': mediaUrl,
      'timestamp': timestamp?.toIso8601String(),
      'position': {
        'row': position.row,
        'col': position.col,
        'width': position.width,
        'height': position.height,
      },
      'likesCount': likesCount,
      'commentsCount': commentsCount,
    };
  }
}

/// `MosaicLikeState`: respuesta de `likeMosaicItem` y `unlikeMosaicItem`.
class MosaicLikeStateModel extends MosaicLikeState {
  const MosaicLikeStateModel({
    required super.itemId,
    required super.likesCount,
    required super.isLiked,
  });

  factory MosaicLikeStateModel.fromJson(Map<String, dynamic> json) {
    return MosaicLikeStateModel(
      itemId: json['itemId'] as String? ?? '',
      likesCount: json['likesCount'] as int? ?? 0,
      isLiked: json['isLiked'] as bool? ?? false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'itemId': itemId,
      'likesCount': likesCount,
      'isLiked': isLiked,
    };
  }
}
