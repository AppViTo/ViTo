import 'package:equatable/equatable.dart';

/// Tipos de imagen que admiten `uploadMedia` y `createMosaicPost` mientras
/// rija la excepción A3 (T051): sin video ni otros formatos.
const Set<String> mosaicAllowedImageTypes = {
  'image/jpeg',
  'image/png',
  'image/webp',
};

/// Tamaño máximo que acepta `uploadMedia` (10 MB).
const int mosaicMaxUploadBytes = 10 * 1024 * 1024;

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

  /// Copia con el estado de like que devuelve `likeMosaicItem` o
  /// `unlikeMosaicItem` (`MosaicLikeState`).
  MosaicFeedItem withLikeState(MosaicLikeState state) {
    return MosaicFeedItem(
      id: id,
      eventId: eventId,
      userId: userId,
      username: username,
      displayName: displayName,
      avatar: avatar,
      type: type,
      content: content,
      mediaUrls: mediaUrls,
      timestamp: timestamp,
      likesCount: state.likesCount,
      commentsCount: commentsCount,
      isLiked: state.isLiked,
    );
  }

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
  final MosaicItemType type;
  final String? content;
  final String? mediaUrl;
  final DateTime? timestamp;
  final MosaicItemPosition position;
  final int likesCount;
  final int commentsCount;
  
  const MosaicItem({
    required this.id,
    required this.eventId,
    required this.userId,
    required this.username,
    required this.displayName,
    this.avatar,
    this.type = MosaicItemType.photo,
    this.content,
    this.mediaUrl,
    this.timestamp,
    this.position = const MosaicItemPosition(row: 0, col: 0),
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
        type,
        content,
        mediaUrl,
        timestamp,
        position,
        likesCount,
        commentsCount,
      ];
}

/// `MosaicItem.type` del contrato: incluye `HIGHLIGHT` y no tiene
/// `LIVE_UPDATE` ni `ANNOUNCEMENT`, a diferencia de [FeedItemType].
enum MosaicItemType {
  checkIn,
  post,
  photo,
  reaction,
  story,
  highlight,
}

/// Celda (y tamaño en celdas) que ocupa un [MosaicItem] en la grilla.
class MosaicItemPosition extends Equatable {
  final int row;
  final int col;
  final int width;
  final int height;

  const MosaicItemPosition({
    required this.row,
    required this.col,
    this.width = 1,
    this.height = 1,
  });

  @override
  List<Object?> get props => [row, col, width, height];
}

/// Respuesta de `likeMosaicItem` y `unlikeMosaicItem` (idempotentes).
class MosaicLikeState extends Equatable {
  final String itemId;
  final int likesCount;
  final bool isLiked;

  const MosaicLikeState({
    required this.itemId,
    required this.likesCount,
    required this.isLiked,
  });

  @override
  List<Object?> get props => [itemId, likesCount, isLiked];
}
