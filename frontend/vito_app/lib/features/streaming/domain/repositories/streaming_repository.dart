import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../entities/streaming.dart';

abstract class StreamingRepository {
  Future<Either<Failure, LiveStream>> createStream({
    required String eventId,
    required String title,
    String? description,
    StreamSettings? settings,
  });
  
  Future<Either<Failure, LiveStream>> startStream(String streamId);
  
  Future<Either<Failure, LiveStream>> stopStream(String streamId);
  
  Future<Either<Failure, LiveStream>> getStream(String streamId);
  
  Future<Either<Failure, LiveStream>> getStreamByEventId(String eventId);
  
  Future<Either<Failure, List<LiveStream>>> getLiveStreams({
    int page = 0,
    int size = 20,
  });
  
  Future<Either<Failure, Reaction>> addReaction({
    required String streamId,
    required ReactionType type,
    String? emoji,
  });
  
  Future<Either<Failure, List<Reaction>>> getReactions({
    required String streamId,
    int page = 0,
    int size = 50,
  });
  
  Future<Either<Failure, ChatMessage>> sendChatMessage({
    required String streamId,
    required String message,
  });
  
  Future<Either<Failure, List<ChatMessage>>> getChatMessages({
    required String streamId,
    int page = 0,
    int size = 50,
  });
  
  Future<Either<Failure, StreamInfo>> getStreamInfo(String streamId);
  
  Stream<Reaction> get reactionStream;
  Stream<ChatMessage> get chatStream;
  Stream<int> get viewerCountStream;
  
  void connectToWebSocket(String streamId, String baseUrl);
  void disconnectFromWebSocket();
}

class StreamInfo {
  final LiveStream stream;
  final int currentViewers;
  final int totalReactions;
  final int totalMessages;
  final int duration;
  
  StreamInfo({
    required this.stream,
    required this.currentViewers,
    required this.totalReactions,
    required this.totalMessages,
    required this.duration,
  });
}
