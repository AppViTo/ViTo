import '../../domain/entities/streaming.dart';
import '../../domain/repositories/streaming_repository.dart';

class LiveStreamModel extends LiveStream {
  const LiveStreamModel({
    required super.id,
    required super.eventId,
    required super.organizerId,
    required super.title,
    super.description,
    super.status,
    super.settings,
    super.stats,
    super.streamKey,
    super.playbackUrl,
    super.startedAt,
    super.endedAt,
    super.createdAt,
  });
  
  factory LiveStreamModel.fromJson(Map<String, dynamic> json) {
    return LiveStreamModel(
      id: json['id'] ?? '',
      eventId: json['eventId'] ?? '',
      organizerId: json['organizerId'] ?? '',
      title: json['title'] ?? '',
      description: json['description'],
      status: _parseStatus(json['status']),
      settings: json['settings'] != null 
          ? StreamSettingsModel.fromJson(json['settings']) 
          : null,
      stats: json['stats'] != null 
          ? StreamStatsModel.fromJson(json['stats']) 
          : null,
      streamKey: json['streamKey'],
      playbackUrl: json['playbackUrl'],
      startedAt: json['startedAt'] != null 
          ? DateTime.parse(json['startedAt']) 
          : null,
      endedAt: json['endedAt'] != null 
          ? DateTime.parse(json['endedAt']) 
          : null,
      createdAt: json['createdAt'] != null 
          ? DateTime.parse(json['createdAt']) 
          : null,
    );
  }
  
  static StreamStatus _parseStatus(String? status) {
    switch (status) {
      case 'CONNECTING':
        return StreamStatus.connecting;
      case 'LIVE':
        return StreamStatus.live;
      case 'BUFFERING':
        return StreamStatus.buffering;
      case 'ENDED':
        return StreamStatus.ended;
      case 'ERROR':
        return StreamStatus.error;
      default:
        return StreamStatus.idle;
    }
  }
}

class StreamSettingsModel extends StreamSettings {
  const StreamSettingsModel({
    super.enableChat,
    super.enableReactions,
    super.enableRecording,
    super.maxViewers,
    super.chatSlowMode,
  });
  
  factory StreamSettingsModel.fromJson(Map<String, dynamic> json) {
    return StreamSettingsModel(
      enableChat: json['enableChat'] ?? true,
      enableReactions: json['enableReactions'] ?? true,
      enableRecording: json['enableRecording'] ?? true,
      maxViewers: json['maxViewers'] ?? 1000,
      chatSlowMode: json['chatSlowMode'] ?? 0,
    );
  }
}

class StreamStatsModel extends StreamStats {
  const StreamStatsModel({
    super.currentViewers,
    super.totalViewers,
    super.peakViewers,
    super.likesCount,
    super.commentsCount,
    super.sharesCount,
    super.duration,
  });
  
  factory StreamStatsModel.fromJson(Map<String, dynamic> json) {
    return StreamStatsModel(
      currentViewers: json['currentViewers'] ?? 0,
      totalViewers: json['totalViewers'] ?? 0,
      peakViewers: json['peakViewers'] ?? 0,
      likesCount: json['likesCount'] ?? 0,
      commentsCount: json['commentsCount'] ?? 0,
      sharesCount: json['sharesCount'] ?? 0,
      duration: json['duration'] ?? 0,
    );
  }
}

class ReactionModel extends Reaction {
  const ReactionModel({
    required super.id,
    required super.streamId,
    required super.userId,
    required super.username,
    required super.displayName,
    super.avatar,
    required super.type,
    required super.emoji,
    super.timestamp,
    super.x,
    super.y,
  });
  
  factory ReactionModel.fromJson(Map<String, dynamic> json) {
    return ReactionModel(
      id: json['id'] ?? '',
      streamId: json['streamId'] ?? '',
      userId: json['userId'] ?? '',
      username: json['username'] ?? '',
      displayName: json['displayName'] ?? '',
      avatar: json['avatar'],
      type: _parseType(json['type']),
      emoji: json['emoji'] ?? '👍',
      timestamp: json['timestamp'] != null 
          ? DateTime.parse(json['timestamp']) 
          : null,
      x: (json['x'] ?? 0).toDouble(),
      y: (json['y'] ?? 0).toDouble(),
    );
  }
  
  static ReactionType _parseType(String? type) {
    switch (type) {
      case 'HEART':
        return ReactionType.heart;
      case 'FIRE':
        return ReactionType.fire;
      case 'CLAP':
        return ReactionType.clap;
      case 'CELEBRATION':
        return ReactionType.celebration;
      case 'CUSTOM':
        return ReactionType.custom;
      default:
        return ReactionType.like;
    }
  }
}

class ChatMessageModel extends ChatMessage {
  const ChatMessageModel({
    required super.id,
    required super.streamId,
    required super.userId,
    required super.username,
    required super.displayName,
    super.avatar,
    required super.message,
    super.type,
    super.timestamp,
    super.isDeleted,
  });
  
  factory ChatMessageModel.fromJson(Map<String, dynamic> json) {
    return ChatMessageModel(
      id: json['id'] ?? '',
      streamId: json['streamId'] ?? '',
      userId: json['userId'] ?? '',
      username: json['username'] ?? '',
      displayName: json['displayName'] ?? '',
      avatar: json['avatar'],
      message: json['message'] ?? '',
      type: _parseType(json['type']),
      timestamp: json['timestamp'] != null 
          ? DateTime.parse(json['timestamp']) 
          : null,
      isDeleted: json['isDeleted'] ?? false,
    );
  }
  
  static MessageType _parseType(String? type) {
    switch (type) {
      case 'SUPER_CHAT':
        return MessageType.superChat;
      case 'SYSTEM':
        return MessageType.system;
      case 'JOIN':
        return MessageType.join;
      case 'LEAVE':
        return MessageType.leave;
      default:
        return MessageType.normal;
    }
  }
}

class StreamInfoModel extends StreamInfo {
  StreamInfoModel({
    required super.stream,
    required super.currentViewers,
    required super.totalReactions,
    required super.totalMessages,
    required super.duration,
  });
  
  factory StreamInfoModel.fromJson(Map<String, dynamic> json) {
    return StreamInfoModel(
      stream: LiveStreamModel.fromJson(json['stream']),
      currentViewers: json['currentViewers'] ?? 0,
      totalReactions: json['totalReactions'] ?? 0,
      totalMessages: json['totalMessages'] ?? 0,
      duration: json['duration'] ?? 0,
    );
  }
}
