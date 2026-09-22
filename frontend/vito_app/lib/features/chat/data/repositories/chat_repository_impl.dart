import 'dart:async';
import '../../../../config/env/env.dart';
import '../../domain/entities/chat_message.dart';
import '../../domain/entities/chat_room.dart';
import '../../domain/repositories/chat_repository.dart';
import '../datasources/chat_remote_datasource.dart';

class ChatRepositoryImpl implements ChatRepository {
  final ChatRemoteDataSource remoteDataSource;
  
  final _messageController = StreamController<ChatMessage>.broadcast();
  final _roomController = StreamController<ChatRoom>.broadcast();
  
  ChatRepositoryImpl({required this.remoteDataSource});
  
  @override
  Stream<ChatMessage> get messageStream => _messageController.stream;
  
  @override
  Stream<ChatRoom> get roomUpdates => _roomController.stream;
  
  @override
  void connect(String userId) {
    remoteDataSource.messageStream.listen((message) {
      _messageController.add(message.toEntity());
    });
    
    remoteDataSource.roomUpdates.listen((room) {
      _roomController.add(room.toEntity());
    });
    
    remoteDataSource.connect(userId, Env.wsBaseUrl);
  }
  
  @override
  void disconnect() {
    remoteDataSource.disconnect();
    _messageController.close();
    _roomController.close();
  }
  
  @override
  Future<ChatRoom> createRoom({
    required String eventId,
    String? name,
    RoomType type = RoomType.EVENT_CHAT,
    List<String> participantIds = const [],
  }) async {
    final room = await remoteDataSource.createRoom(
      eventId: eventId,
      name: name,
      type: type.name,
      participantIds: participantIds,
    );
    return room.toEntity();
  }
  
  @override
  Future<ChatRoom?> getRoomById(String roomId) async {
    final room = await remoteDataSource.getRoomById(roomId);
    return room?.toEntity();
  }
  
  @override
  Future<List<ChatRoom>> getRoomsByEventId(String eventId) async {
    final rooms = await remoteDataSource.getRoomsByEventId(eventId);
    return rooms.map((room) => room.toEntity()).toList();
  }
  
  @override
  Future<List<ChatRoom>> getRoomsByUserId(String userId) async {
    final rooms = await remoteDataSource.getRoomsByUserId(userId);
    return rooms.map((room) => room.toEntity()).toList();
  }
  
  @override
  Future<ChatRoom> joinRoom(String roomId) async {
    final room = await remoteDataSource.joinRoom(roomId);
    remoteDataSource.joinRoomViaWebSocket(roomId);
    return room.toEntity();
  }
  
  @override
  Future<ChatRoom> leaveRoom(String roomId) async {
    final room = await remoteDataSource.leaveRoom(roomId);
    remoteDataSource.leaveRoomViaWebSocket(roomId);
    return room.toEntity();
  }
  
  @override
  Future<ChatMessage> sendMessage({
    required String roomId,
    required String content,
    MessageType type = MessageType.TEXT,
    String? mediaUrl,
  }) async {
    final message = await remoteDataSource.sendMessage(
      roomId: roomId,
      content: content,
      type: type.name,
      mediaUrl: mediaUrl,
    );
    return message.toEntity();
  }
  
  @override
  Future<List<ChatMessage>> getMessages({
    required String roomId,
    int page = 0,
    int size = 50,
  }) async {
    final messages = await remoteDataSource.getMessages(
      roomId: roomId,
      page: page,
      size: size,
    );
    return messages.map((message) => message.toEntity()).toList();
  }
  
  @override
  Future<ChatMessage?> getMessageById(String messageId) async {
    // Would need to implement in datasource
    return null;
  }
  
  @override
  Future<ChatMessage> editMessage(String messageId, String content) async {
    // Would need to implement in datasource
    throw UnimplementedError();
  }
  
  @override
  Future<void> deleteMessage(String messageId) async {
    // Would need to implement in datasource
    throw UnimplementedError();
  }
  
  @override
  Future<void> markAsRead(String roomId) async {
    await remoteDataSource.markAsRead(roomId);
  }
}
