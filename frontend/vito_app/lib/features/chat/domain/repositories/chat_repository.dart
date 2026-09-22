import '../entities/chat_message.dart';
import '../entities/chat_room.dart';

abstract class ChatRepository {
  // Room operations
  Future<ChatRoom> createRoom({
    required String eventId,
    String? name,
    RoomType type = RoomType.EVENT_CHAT,
    List<String> participantIds = const [],
  });
  
  Future<ChatRoom?> getRoomById(String roomId);
  Future<List<ChatRoom>> getRoomsByEventId(String eventId);
  Future<List<ChatRoom>> getRoomsByUserId(String userId);
  Future<ChatRoom> joinRoom(String roomId);
  Future<ChatRoom> leaveRoom(String roomId);
  
  // Message operations
  Future<ChatMessage> sendMessage({
    required String roomId,
    required String content,
    MessageType type = MessageType.TEXT,
    String? mediaUrl,
  });
  
  Future<List<ChatMessage>> getMessages({
    required String roomId,
    int page = 0,
    int size = 50,
  });
  
  Future<ChatMessage?> getMessageById(String messageId);
  Future<ChatMessage> editMessage(String messageId, String content);
  Future<void> deleteMessage(String messageId);
  Future<void> markAsRead(String roomId);
  
  // WebSocket
  Stream<ChatMessage> get messageStream;
  Stream<ChatRoom> get roomUpdates;
  void connect(String userId);
  void disconnect();
}
