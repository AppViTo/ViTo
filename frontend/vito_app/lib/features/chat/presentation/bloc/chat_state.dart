import 'package:equatable/equatable.dart';
import '../../domain/entities/chat_message.dart';
import '../../domain/entities/chat_room.dart';

abstract class ChatState extends Equatable {
  const ChatState();
  
  @override
  List<Object?> get props => [];
}

class ChatInitial extends ChatState {}

class ChatLoading extends ChatState {}

class ChatError extends ChatState {
  final String message;
  
  const ChatError({required this.message});
  
  @override
  List<Object?> get props => [message];
}

class RoomsLoaded extends ChatState {
  final List<ChatRoom> rooms;
  
  const RoomsLoaded({required this.rooms});
  
  @override
  List<Object?> get props => [rooms];
  
  RoomsLoaded copyWith({
    List<ChatRoom>? rooms,
  }) {
    return RoomsLoaded(
      rooms: rooms ?? this.rooms,
    );
  }
}

class RoomJoined extends ChatState {
  final ChatRoom room;
  final List<ChatMessage> messages;
  
  const RoomJoined({
    required this.room,
    this.messages = const [],
  });
  
  @override
  List<Object?> get props => [room, messages];
  
  RoomJoined copyWith({
    ChatRoom? room,
    List<ChatMessage>? messages,
  }) {
    return RoomJoined(
      room: room ?? this.room,
      messages: messages ?? this.messages,
    );
  }
}

class RoomLeft extends ChatState {}

class MessagesLoaded extends ChatState {
  final List<ChatMessage> messages;
  final bool hasMore;
  
  const MessagesLoaded({
    required this.messages,
    this.hasMore = true,
  });
  
  @override
  List<Object?> get props => [messages, hasMore];
}
