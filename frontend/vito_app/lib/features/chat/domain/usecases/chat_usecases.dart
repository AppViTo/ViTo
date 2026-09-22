import '../entities/chat_message.dart';
import '../entities/chat_room.dart';
import '../repositories/chat_repository.dart';

class SendMessageUseCase {
  final ChatRepository repository;
  
  SendMessageUseCase(this.repository);
  
  Future<ChatMessage> call({
    required String roomId,
    required String content,
    MessageType type = MessageType.TEXT,
    String? mediaUrl,
  }) async {
    return await repository.sendMessage(
      roomId: roomId,
      content: content,
      type: type,
      mediaUrl: mediaUrl,
    );
  }
}

class GetMessagesUseCase {
  final ChatRepository repository;
  
  GetMessagesUseCase(this.repository);
  
  Future<List<ChatMessage>> call({
    required String roomId,
    int page = 0,
    int size = 50,
  }) async {
    return await repository.getMessages(
      roomId: roomId,
      page: page,
      size: size,
    );
  }
}

class CreateRoomUseCase {
  final ChatRepository repository;
  
  CreateRoomUseCase(this.repository);
  
  Future<ChatRoom> call({
    required String eventId,
    String? name,
    RoomType type = RoomType.EVENT_CHAT,
    List<String> participantIds = const [],
  }) async {
    return await repository.createRoom(
      eventId: eventId,
      name: name,
      type: type,
      participantIds: participantIds,
    );
  }
}

class JoinRoomUseCase {
  final ChatRepository repository;
  
  JoinRoomUseCase(this.repository);
  
  Future<ChatRoom> call(String roomId) async {
    return await repository.joinRoom(roomId);
  }
}

class LeaveRoomUseCase {
  final ChatRepository repository;
  
  LeaveRoomUseCase(this.repository);
  
  Future<ChatRoom> call(String roomId) async {
    return await repository.leaveRoom(roomId);
  }
}
