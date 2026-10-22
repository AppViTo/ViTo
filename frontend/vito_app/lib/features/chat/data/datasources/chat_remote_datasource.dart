import 'dart:async';
import 'dart:convert';
import 'package:dio/dio.dart';
import '../../../../core/network/ws_connection.dart';
import '../models/chat_message_model.dart';
import '../models/chat_room_model.dart';

class ChatRemoteDataSource {
  final Dio dio;
  final WsConnection wsConnection;
  final _messageController = StreamController<ChatMessageModel>.broadcast();
  final _roomController = StreamController<ChatRoomModel>.broadcast();
  
  ChatRemoteDataSource({
    required this.dio,
    required this.wsConnection,
  });
  
  Stream<ChatMessageModel> get messageStream => _messageController.stream;
  Stream<ChatRoomModel> get roomUpdates => _roomController.stream;
  
  // Room operations
  Future<ChatRoomModel> createRoom({
    required String eventId,
    String? name,
    String type = 'EVENT_CHAT',
    List<String> participantIds = const [],
  }) async {
    final response = await dio.post(
      '/chat/rooms',
      data: {
        'eventId': eventId,
        'name': name,
        'type': type,
        'participantIds': participantIds,
      },
    );
    return ChatRoomModel.fromJson(response.data);
  }
  
  Future<ChatRoomModel?> getRoomById(String roomId) async {
    try {
      final response = await dio.get('/chat/rooms/$roomId');
      return ChatRoomModel.fromJson(response.data);
    } catch (e) {
      return null;
    }
  }
  
  Future<List<ChatRoomModel>> getRoomsByEventId(String eventId) async {
    final response = await dio.get('/chat/rooms/event/$eventId');
    return (response.data as List)
        .map((json) => ChatRoomModel.fromJson(json))
        .toList();
  }
  
  Future<List<ChatRoomModel>> getRoomsByUserId(String userId) async {
    final response = await dio.get('/chat/rooms/user');
    return (response.data as List)
        .map((json) => ChatRoomModel.fromJson(json))
        .toList();
  }
  
  Future<ChatRoomModel> joinRoom(String roomId) async {
    final response = await dio.post('/chat/rooms/$roomId/join');
    return ChatRoomModel.fromJson(response.data);
  }
  
  Future<ChatRoomModel> leaveRoom(String roomId) async {
    final response = await dio.post('/chat/rooms/$roomId/leave');
    return ChatRoomModel.fromJson(response.data);
  }
  
  // Message operations
  Future<ChatMessageModel> sendMessage({
    required String roomId,
    required String content,
    String type = 'TEXT',
    String? mediaUrl,
  }) async {
    final response = await dio.post(
      '/chat/messages',
      data: {
        'roomId': roomId,
        'content': content,
        'type': type,
        'mediaUrl': mediaUrl,
      },
    );
    return ChatMessageModel.fromJson(response.data);
  }
  
  Future<List<ChatMessageModel>> getMessages({
    required String roomId,
    int page = 0,
    int size = 50,
  }) async {
    final response = await dio.get(
      '/chat/rooms/$roomId/messages',
      queryParameters: {'page': page, 'size': size},
    );
    return (response.data as List)
        .map((json) => ChatMessageModel.fromJson(json))
        .toList();
  }
  
  Future<void> markAsRead(String roomId) async {
    await dio.post('/chat/rooms/$roomId/read');
  }
  
  // WebSocket
  void connect() {
    unawaited(wsConnection.connect(
      '/ws/chat',
      onData: _handleSocketMessage,
    ));
  }
  
  void _handleSocketMessage(dynamic data) {
    final json = jsonDecode(data);
    if (json['type'] == 'MESSAGE') {
      final message = ChatMessageModel.fromJson(json['data']);
      _messageController.add(message);
    } else if (json['type'] == 'ROOM_UPDATE') {
      final room = ChatRoomModel.fromJson(json['data']);
      _roomController.add(room);
    }
  }
  
  void disconnect() {
    unawaited(wsConnection.disconnect());
  }
  
  void sendMessageViaWebSocket({
    required String roomId,
    required String content,
    String type = 'TEXT',
  }) {
    wsConnection.send(jsonEncode({
      'type': 'SEND_MESSAGE',
      'roomId': roomId,
      'content': content,
      'messageType': type,
    }));
  }
  
  void sendTypingIndicator(String roomId, bool isTyping) {
    wsConnection.send(jsonEncode({
      'type': 'TYPING',
      'roomId': roomId,
      'isTyping': isTyping,
    }));
  }
  
  void joinRoomViaWebSocket(String roomId) {
    wsConnection.send(jsonEncode({
      'type': 'JOIN_ROOM',
      'roomId': roomId,
    }));
  }
  
  void leaveRoomViaWebSocket(String roomId) {
    wsConnection.send(jsonEncode({
      'type': 'LEAVE_ROOM',
      'roomId': roomId,
    }));
  }
}
