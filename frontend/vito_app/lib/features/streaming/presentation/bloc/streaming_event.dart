import 'package:equatable/equatable.dart';

import '../../domain/entities/streaming.dart';

abstract class StreamingEvent extends Equatable {
  const StreamingEvent();
  
  @override
  List<Object?> get props => [];
}

class CreateStream extends StreamingEvent {
  final String eventId;
  final String title;
  final String? description;
  final StreamSettings? settings;
  
  const CreateStream({
    required this.eventId,
    required this.title,
    this.description,
    this.settings,
  });
  
  @override
  List<Object?> get props => [eventId, title, description, settings];
}

class StartStream extends StreamingEvent {
  final String streamId;
  
  const StartStream({required this.streamId});
  
  @override
  List<Object?> get props => [streamId];
}

class StopStream extends StreamingEvent {
  final String streamId;
  
  const StopStream({required this.streamId});
  
  @override
  List<Object?> get props => [streamId];
}

class LoadStream extends StreamingEvent {
  final String streamId;
  
  const LoadStream({required this.streamId});
  
  @override
  List<Object?> get props => [streamId];
}

class LoadLiveStreams extends StreamingEvent {
  final int page;
  final int size;
  
  const LoadLiveStreams({this.page = 0, this.size = 20});
  
  @override
  List<Object?> get props => [page, size];
}

class SendReaction extends StreamingEvent {
  final String streamId;
  final ReactionType type;
  final String? emoji;
  
  const SendReaction({
    required this.streamId,
    required this.type,
    this.emoji,
  });
  
  @override
  List<Object?> get props => [streamId, type, emoji];
}

class SendChatMessage extends StreamingEvent {
  final String streamId;
  final String message;
  
  const SendChatMessage({
    required this.streamId,
    required this.message,
  });
  
  @override
  List<Object?> get props => [streamId, message];
}

class ReactionReceived extends StreamingEvent {
  final Reaction reaction;
  
  const ReactionReceived({required this.reaction});
  
  @override
  List<Object?> get props => [reaction];
}

class ChatMessageReceived extends StreamingEvent {
  final ChatMessage message;
  
  const ChatMessageReceived({required this.message});
  
  @override
  List<Object?> get props => [message];
}

class ViewerCountUpdated extends StreamingEvent {
  final int count;
  
  const ViewerCountUpdated({required this.count});
  
  @override
  List<Object?> get props => [count];
}

class ConnectToStream extends StreamingEvent {
  final String streamId;
  final String baseUrl;
  
  const ConnectToStream({
    required this.streamId,
    required this.baseUrl,
  });
  
  @override
  List<Object?> get props => [streamId, baseUrl];
}

class DisconnectFromStream extends StreamingEvent {
  const DisconnectFromStream();
  
  @override
  List<Object?> get props => [];
}
