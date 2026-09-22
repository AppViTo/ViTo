import 'package:equatable/equatable.dart';

class LiveStream extends Equatable {
  final String id;
  final String eventId;
  final String organizerId;
  final String title;
  final String? description;
  final StreamStatus status;
  final StreamSettings? settings;
  final StreamStats? stats;
  final String? streamKey;
  final String? playbackUrl;
  final DateTime? startedAt;
  final DateTime? endedAt;
  final DateTime? createdAt;
  
  const LiveStream({
    required this.id,
    required this.eventId,
    required this.organizerId,
    required this.title,
    this.description,
    this.status = StreamStatus.idle,
    this.settings,
    this.stats,
    this.streamKey,
    this.playbackUrl,
    this.startedAt,
    this.endedAt,
    this.createdAt,
  });
  
  bool get isLive => status == StreamStatus.live;
  
  @override
  List<Object?> get props => [
        id,
        eventId,
        organizerId,
        title,
        description,
        status,
        settings,
        stats,
        streamKey,
        playbackUrl,
        startedAt,
        endedAt,
        createdAt,
      ];
}

enum StreamStatus {
  idle,
  connecting,
  live,
  buffering,
  ended,
  error,
}

class StreamSettings extends Equatable {
  final bool enableChat;
  final bool enableReactions;
  final bool enableRecording;
  final int maxViewers;
  final int chatSlowMode;
  
  const StreamSettings({
    this.enableChat = true,
    this.enableReactions = true,
    this.enableRecording = true,
    this.maxViewers = 1000,
    this.chatSlowMode = 0,
  });
  
  @override
  List<Object?> get props => [
        enableChat,
        enableReactions,
        enableRecording,
        maxViewers,
        chatSlowMode,
      ];
}

class StreamStats extends Equatable {
  final int currentViewers;
  final int totalViewers;
  final int peakViewers;
  final int likesCount;
  final int commentsCount;
  final int sharesCount;
  final int duration;
  
  const StreamStats({
    this.currentViewers = 0,
    this.totalViewers = 0,
    this.peakViewers = 0,
    this.likesCount = 0,
    this.commentsCount = 0,
    this.sharesCount = 0,
    this.duration = 0,
  });
  
  @override
  List<Object?> get props => [
        currentViewers,
        totalViewers,
        peakViewers,
        likesCount,
        commentsCount,
        sharesCount,
        duration,
      ];
}

class Reaction extends Equatable {
  final String id;
  final String streamId;
  final String userId;
  final String username;
  final String displayName;
  final String? avatar;
  final ReactionType type;
  final String emoji;
  final DateTime? timestamp;
  final double x;
  final double y;
  
  const Reaction({
    required this.id,
    required this.streamId,
    required this.userId,
    required this.username,
    required this.displayName,
    this.avatar,
    required this.type,
    required this.emoji,
    this.timestamp,
    this.x = 0,
    this.y = 0,
  });
  
  @override
  List<Object?> get props => [
        id,
        streamId,
        userId,
        username,
        displayName,
        avatar,
        type,
        emoji,
        timestamp,
        x,
        y,
      ];
}

enum ReactionType {
  like,
  heart,
  fire,
  clap,
  celebration,
  custom,
}

class ChatMessage extends Equatable {
  final String id;
  final String streamId;
  final String userId;
  final String username;
  final String displayName;
  final String? avatar;
  final String message;
  final MessageType type;
  final DateTime? timestamp;
  final bool isDeleted;
  
  const ChatMessage({
    required this.id,
    required this.streamId,
    required this.userId,
    required this.username,
    required this.displayName,
    this.avatar,
    required this.message,
    this.type = MessageType.normal,
    this.timestamp,
    this.isDeleted = false,
  });
  
  @override
  List<Object?> get props => [
        id,
        streamId,
        userId,
        username,
        displayName,
        avatar,
        message,
        type,
        timestamp,
        isDeleted,
      ];
}

enum MessageType {
  normal,
  superChat,
  system,
  join,
  leave,
}
