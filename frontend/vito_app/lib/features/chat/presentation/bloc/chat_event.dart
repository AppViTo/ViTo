import 'package:equatable/equatable.dart';
import '../../domain/entities/chat_message.dart';
import '../../domain/entities/chat_room.dart';

abstract class ChatEvent extends Equatable {
  const ChatEvent();
  
  @override
  List<Object?> get props => [];
}

class LoadRooms extends ChatEvent {
  final String userId;
  
  const LoadRooms({required this.userId});
  
  @override
  List<Object?> get props => [userId];
}

class JoinRoom extends ChatEvent {
  final String roomId;
  
  const JoinRoom({required this.roomId});
  
  @override
  List<Object?> get props => [roomId];
}

class LeaveRoom extends ChatEvent {
  final String roomId;
  
  const LeaveRoom({required this.roomId});
  
  @override
  List<Object?> get props => [roomId];
}

class LoadMessages extends ChatEvent {
  final String roomId;
  final int page;
  
  const LoadMessages({required this.roomId, this.page = 0});
  
  @override
  List<Object?> get props => [roomId, page];
}

class SendMessage extends ChatEvent {
  final String roomId;
  final String content;
  final MessageType type;
  final String? mediaUrl;
  
  const SendMessage({
    required this.roomId,
    required this.content,
    this.type = MessageType.TEXT,
    this.mediaUrl,
  });
  
  @override
  List<Object?> get props => [roomId, content, type, mediaUrl];
}

class MessageReceived extends ChatEvent {
  final ChatMessage message;
  
  const MessageReceived({required this.message});
  
  @override
  List<Object?> get props => [message];
}

class RoomUpdated extends ChatEvent {
  final ChatRoom room;
  
  const RoomUpdated({required this.room});
  
  @override
  List<Object?> get props => [room];
}

class SendTypingIndicator extends ChatEvent {
  final String roomId;
  final bool isTyping;
  
  const SendTypingIndicator({
    required this.roomId,
    required this.isTyping,
  });
  
  @override
  List<Object?> get props => [roomId, isTyping];
}

class MarkAsRead extends ChatEvent {
  final String roomId;
  
  const MarkAsRead({required this.roomId});
  
  @override
  List<Object?> get props => [roomId];
}
