import '../../domain/entities/archive.dart';

class EventArchiveModel extends EventArchive {
  const EventArchiveModel({
    required super.id,
    required super.eventId,
    required super.organizerId,
    required super.title,
    super.description,
    super.coverUrl,
    super.status,
    super.statistics,
    super.sharing,
    super.eventDate,
    super.archivedAt,
    super.createdAt,
  });
  
  factory EventArchiveModel.fromJson(Map<String, dynamic> json) {
    return EventArchiveModel(
      id: json['id'] ?? '',
      eventId: json['eventId'] ?? '',
      organizerId: json['organizerId'] ?? '',
      title: json['title'] ?? '',
      description: json['description'],
      coverUrl: json['coverUrl'],
      status: _parseStatus(json['status']),
      statistics: json['statistics'] != null
          ? EventStatisticsModel.fromJson(json['statistics'])
          : const EventStatistics(),
      sharing: json['sharing'] != null
          ? SharingSettingsModel.fromJson(json['sharing'])
          : const SharingSettings(),
      eventDate: json['eventDate'] != null
          ? DateTime.parse(json['eventDate'])
          : null,
      archivedAt: json['archivedAt'] != null
          ? DateTime.parse(json['archivedAt'])
          : null,
      createdAt: json['createdAt'] != null
          ? DateTime.parse(json['createdAt'])
          : null,
    );
  }
  
  static ArchiveStatus _parseStatus(String? status) {
    switch (status) {
      case 'PROCESSING':
        return ArchiveStatus.processing;
      case 'COMPLETED':
        return ArchiveStatus.completed;
      case 'FAILED':
        return ArchiveStatus.failed;
      default:
        return ArchiveStatus.pending;
    }
  }
}

class EventStatisticsModel extends EventStatistics {
  const EventStatisticsModel({
    super.totalAttendees,
    super.totalCheckIns,
    super.totalPhotos,
    super.totalReactions,
    super.totalComments,
    super.totalShares,
    super.peakConcurrentViewers,
    super.totalStreamMinutes,
  });
  
  factory EventStatisticsModel.fromJson(Map<String, dynamic> json) {
    return EventStatisticsModel(
      totalAttendees: json['totalAttendees'] ?? 0,
      totalCheckIns: json['totalCheckIns'] ?? 0,
      totalPhotos: json['totalPhotos'] ?? 0,
      totalReactions: json['totalReactions'] ?? 0,
      totalComments: json['totalComments'] ?? 0,
      totalShares: json['totalShares'] ?? 0,
      peakConcurrentViewers: json['peakConcurrentViewers'] ?? 0,
      totalStreamMinutes: json['totalStreamMinutes'] ?? 0,
    );
  }
}

class SharingSettingsModel extends SharingSettings {
  const SharingSettingsModel({
    super.isPublic,
    super.allowComments,
    super.allowReactions,
    super.allowSharing,
    super.shareUrl,
    super.shareCode,
  });
  
  factory SharingSettingsModel.fromJson(Map<String, dynamic> json) {
    return SharingSettingsModel(
      isPublic: json['isPublic'] ?? false,
      allowComments: json['allowComments'] ?? true,
      allowReactions: json['allowReactions'] ?? true,
      allowSharing: json['allowSharing'] ?? true,
      shareUrl: json['shareUrl'],
      shareCode: json['shareCode'],
    );
  }
}

class ArchiveMediaModel extends ArchiveMedia {
  const ArchiveMediaModel({
    required super.id,
    required super.archiveId,
    required super.eventId,
    required super.uploaderId,
    required super.type,
    required super.url,
    super.thumbnailUrl,
    super.caption,
    super.metadata,
    super.tags,
    super.likesCount,
    super.commentsCount,
    super.uploadedAt,
  });
  
  factory ArchiveMediaModel.fromJson(Map<String, dynamic> json) {
    return ArchiveMediaModel(
      id: json['id'] ?? '',
      archiveId: json['archiveId'] ?? '',
      eventId: json['eventId'] ?? '',
      uploaderId: json['uploaderId'] ?? '',
      type: _parseMediaType(json['type']),
      url: json['url'] ?? '',
      thumbnailUrl: json['thumbnailUrl'],
      caption: json['caption'],
      metadata: json['metadata'] != null
          ? MediaMetadataModel.fromJson(json['metadata'])
          : null,
      tags: List<String>.from(json['tags'] ?? []),
      likesCount: json['likesCount'] ?? 0,
      commentsCount: json['commentsCount'] ?? 0,
      uploadedAt: json['uploadedAt'] != null
          ? DateTime.parse(json['uploadedAt'])
          : null,
    );
  }
  
  static MediaType _parseMediaType(String? type) {
    switch (type) {
      case 'VIDEO':
        return MediaType.video;
      case 'STREAM_RECORDING':
        return MediaType.streamRecording;
      case 'LIVE_SCREENSHOT':
        return MediaType.liveScreenshot;
      default:
        return MediaType.photo;
    }
  }
}

class MediaMetadataModel extends MediaMetadata {
  const MediaMetadataModel({
    super.fileSize,
    super.width,
    super.height,
    super.duration,
    super.mimeType,
    super.location,
    super.takenAt,
  });
  
  factory MediaMetadataModel.fromJson(Map<String, dynamic> json) {
    return MediaMetadataModel(
      fileSize: json['fileSize'],
      width: json['width'],
      height: json['height'],
      duration: json['duration'],
      mimeType: json['mimeType'],
      location: json['location'],
      takenAt: json['takenAt'] != null
          ? DateTime.parse(json['takenAt'])
          : null,
    );
  }
}

class ArchiveCommentModel extends ArchiveComment {
  const ArchiveCommentModel({
    required super.id,
    required super.archiveId,
    super.mediaId,
    required super.userId,
    required super.username,
    required super.displayName,
    super.avatar,
    required super.content,
    super.parentId,
    super.likesCount,
    super.createdAt,
    super.updatedAt,
  });
  
  factory ArchiveCommentModel.fromJson(Map<String, dynamic> json) {
    return ArchiveCommentModel(
      id: json['id'] ?? '',
      archiveId: json['archiveId'] ?? '',
      mediaId: json['mediaId'],
      userId: json['userId'] ?? '',
      username: json['username'] ?? '',
      displayName: json['displayName'] ?? '',
      avatar: json['avatar'],
      content: json['content'] ?? '',
      parentId: json['parentId'],
      likesCount: json['likesCount'] ?? 0,
      createdAt: json['createdAt'] != null
          ? DateTime.parse(json['createdAt'])
          : null,
      updatedAt: json['updatedAt'] != null
          ? DateTime.parse(json['updatedAt'])
          : null,
    );
  }
}
