import 'package:equatable/equatable.dart';

import '../../domain/entities/streaming.dart';

abstract class StreamingState extends Equatable {
  const StreamingState();
  
  @override
  List<Object?> get props => [];
}

class StreamingInitial extends StreamingState {
  const StreamingInitial();
}

class StreamingLoading extends StreamingState {
  const StreamingLoading();
}

class StreamCreated extends StreamingState {
  final LiveStream stream;
  
  const StreamCreated({required this.stream});
  
  @override
  List<Object?> get props => [stream];
}

class StreamLoaded extends StreamingState {
  final LiveStream stream;
  
  const StreamLoaded({required this.stream});
  
  @override
  List<Object?> get props => [stream];
}

class StreamActive extends StreamingState {
  final LiveStream stream;
  final List<Reaction> reactions;
  final List<ChatMessage> messages;
  final int viewerCount;
  
  const StreamActive({
    required this.stream,
    this.reactions = const [],
    this.messages = const [],
    this.viewerCount = 0,
  });
  
  StreamActive copyWith({
    LiveStream? stream,
    List<Reaction>? reactions,
    List<ChatMessage>? messages,
    int? viewerCount,
  }) {
    return StreamActive(
      stream: stream ?? this.stream,
      reactions: reactions ?? this.reactions,
      messages: messages ?? this.messages,
      viewerCount: viewerCount ?? this.viewerCount,
    );
  }
  
  @override
  List<Object?> get props => [stream, reactions, messages, viewerCount];
}

class StreamEnded extends StreamingState {
  final LiveStream stream;
  
  const StreamEnded({required this.stream});
  
  @override
  List<Object?> get props => [stream];
}

class LiveStreamsLoaded extends StreamingState {
  final List<LiveStream> streams;
  
  const LiveStreamsLoaded({required this.streams});
  
  @override
  List<Object?> get props => [streams];
}

class ReactionSent extends StreamingState {
  final Reaction reaction;
  
  const ReactionSent({required this.reaction});
  
  @override
  List<Object?> get props => [reaction];
}

class ChatMessageSent extends StreamingState {
  final ChatMessage message;
  
  const ChatMessageSent({required this.message});
  
  @override
  List<Object?> get props => [message];
}

class StreamingError extends StreamingState {
  final String message;
  
  const StreamingError({required this.message});
  
  @override
  List<Object?> get props => [message];
}

class ReactionError extends StreamingState {
  final String message;
  
  const ReactionError({required this.message});
  
  @override
  List<Object?> get props => [message];
}

class ChatError extends StreamingState {
  final String message;
  
  const ChatError({required this.message});
  
  @override
  List<Object?> get props => [message];
}
