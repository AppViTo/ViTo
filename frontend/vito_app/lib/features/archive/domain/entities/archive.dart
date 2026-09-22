import 'package:equatable/equatable.dart';

class EventArchive extends Equatable {
  final String id;
  final String eventId;
  final String organizerId;
  final String title;
  final String? description;
  final String? coverUrl;
  final ArchiveStatus status;
  final EventStatistics statistics;
  final SharingSettings sharing;
  final DateTime? eventDate;
  final DateTime? archivedAt;
  final DateTime? createdAt;
  
  const EventArchive({
    required this.id,
    required this.eventId,
    required this.organizerId,
    required this.title,
    this.description,
    this.coverUrl,
    this.status = ArchiveStatus.pending,
    this.statistics = const EventStatistics(),
    this.sharing = const SharingSettings(),
    this.eventDate,
    this.archivedAt,
    this.createdAt,
  });
  
  bool get isPublic => sharing.isPublic;
  bool get isCompleted => status == ArchiveStatus.completed;
  
  @override
  List<Object?> get props => [
        id,
        eventId,
        organizerId,
        title,
        description,
        coverUrl,
        status,
        statistics,
        sharing,
        eventDate,
        archivedAt,
        createdAt,
      ];
}

enum ArchiveStatus {
  pending,
  processing,
  completed,
  failed,
}

class EventStatistics extends Equatable {
  final int totalAttendees;
  final int totalCheckIns;
  final int totalPhotos;
  final int totalReactions;
  final int totalComments;
  final int totalShares;
  final int peakConcurrentViewers;
  final int totalStreamMinutes;
  
  const EventStatistics({
    this.totalAttendees = 0,
    this.totalCheckIns = 0,
    this.totalPhotos = 0,
    this.totalReactions = 0,
    this.totalComments = 0,
    this.totalShares = 0,
    this.peakConcurrentViewers = 0,
    this.totalStreamMinutes = 0,
  });
  
  @override
  List<Object?> get props => [
        totalAttendees,
        totalCheckIns,
        totalPhotos,
        totalReactions,
        totalComments,
        totalShares,
        peakConcurrentViewers,
        totalStreamMinutes,
      ];
}

class SharingSettings extends Equatable {
  final bool isPublic;
  final bool allowComments;
  final bool allowReactions;
  final bool allowSharing;
  final String? shareUrl;
  final String? shareCode;
  
  const SharingSettings({
    this.isPublic = false,
    this.allowComments = true,
    this.allowReactions = true,
    this.allowSharing = true,
    this.shareUrl,
    this.shareCode,
  });
  
  @override
  List<Object?> get props => [
        isPublic,
        allowComments,
        allowReactions,
        allowSharing,
        shareUrl,
        shareCode,
      ];
}

class ArchiveMedia extends Equatable {
  final String id;
  final String archiveId;
  final String eventId;
  final String uploaderId;
  final MediaType type;
  final String url;
  final String? thumbnailUrl;
  final String? caption;
  final MediaMetadata? metadata;
  final List<String> tags;
  final int likesCount;
  final int commentsCount;
  final DateTime? uploadedAt;
  
  const ArchiveMedia({
    required this.id,
    required this.archiveId,
    required this.eventId,
    required this.uploaderId,
    required this.type,
    required this.url,
    this.thumbnailUrl,
    this.caption,
    this.metadata,
    this.tags = const [],
    this.likesCount = 0,
    this.commentsCount = 0,
    this.uploadedAt,
  });
  
  @override
  List<Object?> get props => [
        id,
        archiveId,
        eventId,
        uploaderId,
        type,
        url,
        thumbnailUrl,
        caption,
        metadata,
        tags,
        likesCount,
        commentsCount,
        uploadedAt,
      ];
}

enum MediaType {
  photo,
  video,
  streamRecording,
  liveScreenshot,
}

class MediaMetadata extends Equatable {
  final int? fileSize;
  final int? width;
  final int? height;
  final int? duration;
  final String? mimeType;
  final String? location;
  final DateTime? takenAt;
  
  const MediaMetadata({
    this.fileSize,
    this.width,
    this.height,
    this.duration,
    this.mimeType,
    this.location,
    this.takenAt,
  });
  
  @override
  List<Object?> get props => [
        fileSize,
        width,
        height,
        duration,
        mimeType,
        location,
        takenAt,
      ];
}

class ArchiveComment extends Equatable {
  final String id;
  final String archiveId;
  final String? mediaId;
  final String userId;
  final String username;
  final String displayName;
  final String? avatar;
  final String content;
  final String? parentId;
  final int likesCount;
  final DateTime? createdAt;
  final DateTime? updatedAt;
  
  const ArchiveComment({
    required this.id,
    required this.archiveId,
    this.mediaId,
    required this.userId,
    required this.username,
    required this.displayName,
    this.avatar,
    required this.content,
    this.parentId,
    this.likesCount = 0,
    this.createdAt,
    this.updatedAt,
  });
  
  @override
  List<Object?> get props => [
        id,
        archiveId,
        mediaId,
        userId,
        username,
        displayName,
        avatar,
        content,
        parentId,
        likesCount,
        createdAt,
        updatedAt,
      ];
}
