import 'dart:async';

import 'package:dartz/dartz.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../../../../core/error/exceptions.dart';
import '../../../../core/error/failures.dart';
import '../datasources/streaming_remote_datasource.dart';
import '../models/streaming_model.dart';
import '../../domain/entities/streaming.dart';
import '../../domain/repositories/streaming_repository.dart';

class StreamingRepositoryImpl implements StreamingRepository {
  final StreamingRemoteDataSource remoteDataSource;
  
  final _reactionController = StreamController<Reaction>.broadcast();
  final _chatController = StreamController<ChatMessage>.broadcast();
  final _viewerCountController = StreamController<int>.broadcast();
  
  WebSocketChannel? _webSocketChannel;
  
  StreamingRepositoryImpl({required this.remoteDataSource});
  
  @override
  Future<Either<Failure, LiveStream>> createStream({
    required String eventId,
    required String title,
    String? description,
    StreamSettings? settings,
  }) async {
    try {
      final stream = await remoteDataSource.createStream(
        eventId: eventId,
        title: title,
        description: description,
        settings: settings != null ? {
          'enableChat': settings.enableChat,
          'enableReactions': settings.enableReactions,
          'enableRecording': settings.enableRecording,
          'maxViewers': settings.maxViewers,
          'chatSlowMode': settings.chatSlowMode,
        } : null,
      );
      return Right(stream);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, LiveStream>> startStream(String streamId) async {
    try {
      final stream = await remoteDataSource.startStream(streamId);
      return Right(stream);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, LiveStream>> stopStream(String streamId) async {
    try {
      final stream = await remoteDataSource.stopStream(streamId);
      return Right(stream);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, LiveStream>> getStream(String streamId) async {
    try {
      final stream = await remoteDataSource.getStream(streamId);
      return Right(stream);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, LiveStream>> getStreamByEventId(String eventId) async {
    try {
      final stream = await remoteDataSource.getStreamByEventId(eventId);
      return Right(stream);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, List<LiveStream>>> getLiveStreams({
    int page = 0,
    int size = 20,
  }) async {
    try {
      final streams = await remoteDataSource.getLiveStreams(page: page, size: size);
      return Right(streams);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, Reaction>> addReaction({
    required String streamId,
    required ReactionType type,
    String? emoji,
  }) async {
    try {
      final reaction = await remoteDataSource.addReaction(
        streamId: streamId,
        type: type.name.toUpperCase(),
        emoji: emoji,
      );
      return Right(reaction);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, List<Reaction>>> getReactions({
    required String streamId,
    int page = 0,
    int size = 50,
  }) async {
    try {
      final reactions = await remoteDataSource.getReactions(
        streamId: streamId,
        page: page,
        size: size,
      );
      return Right(reactions);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, ChatMessage>> sendChatMessage({
    required String streamId,
    required String message,
  }) async {
    try {
      final chatMessage = await remoteDataSource.sendChatMessage(
        streamId: streamId,
        message: message,
      );
      return Right(chatMessage);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, List<ChatMessage>>> getChatMessages({
    required String streamId,
    int page = 0,
    int size = 50,
  }) async {
    try {
      final messages = await remoteDataSource.getChatMessages(
        streamId: streamId,
        page: page,
        size: size,
      );
      return Right(messages);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, StreamInfo>> getStreamInfo(String streamId) async {
    try {
      final info = await remoteDataSource.getStreamInfo(streamId);
      return Right(info);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Stream<Reaction> get reactionStream => _reactionController.stream;
  
  @override
  Stream<ChatMessage> get chatStream => _chatController.stream;
  
  @override
  Stream<int> get viewerCountStream => _viewerCountController.stream;
  
  @override
  void connectToWebSocket(String streamId, String baseUrl) {
    final wsUrl = baseUrl.replaceFirst('http', 'ws') + '/ws/streaming/$streamId';
    _webSocketChannel = WebSocketChannel.connect(Uri.parse(wsUrl));
    
    _webSocketChannel!.stream.listen(
      (message) {
        final data = Map<String, dynamic>.from(message as Map);
        
        if (data['type'] == 'REACTION') {
          final reaction = ReactionModel.fromJson(data['data']);
          _reactionController.add(reaction);
        } else if (data['type'] == 'CHAT') {
          final chatMessage = ChatMessageModel.fromJson(data['data']);
          _chatController.add(chatMessage);
        } else if (data['type'] == 'VIEWER_COUNT') {
          final count = data['data']['count'] as int;
          _viewerCountController.add(count);
        }
      },
      onError: (error) {
        print('WebSocket error: $error');
      },
      onDone: () {
        print('WebSocket connection closed');
      },
    );
  }
  
  @override
  void disconnectFromWebSocket() {
    _webSocketChannel?.sink.close();
    _webSocketChannel = null;
  }
  
  void dispose() {
    disconnectFromWebSocket();
    _reactionController.close();
    _chatController.close();
    _viewerCountController.close();
  }
}
