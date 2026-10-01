import '../../domain/entities/chat_room.dart';

class ChatRoomModel extends ChatRoom {
  const ChatRoomModel({
    required super.id,
    required super.eventId,
    super.name,
    required super.type,
    required super.participantIds,
    required super.createdBy,
    required super.createdAt,
    required super.lastMessageAt,
    super.unreadCount,
    super.lastMessage,
    super.lastMessageSender,
  });
  
  factory ChatRoomModel.fromJson(Map<String, dynamic> json) {
    return ChatRoomModel(
      id: json['id'] ?? '',
      eventId: json['eventId'] ?? '',
      name: json['name'],
      type: RoomType.values.firstWhere(
        (e) => e.name == json['type'],
        orElse: () => RoomType.EVENT_CHAT,
      ),
      participantIds: List<String>.from(json['participantIds'] ?? []),
      createdBy: json['createdBy'] ?? '',
      createdAt: json['createdAt'] != null 
          ? DateTime.parse(json['createdAt']) 
          : DateTime.now(),
      lastMessageAt: json['lastMessageAt'] != null 
          ? DateTime.parse(json['lastMessageAt']) 
          : DateTime.now(),
      unreadCount: json['unreadCount'] ?? 0,
      lastMessage: json['lastMessage'],
      lastMessageSender: json['lastMessageSender'],
    );
  }
  
  @override
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'eventId': eventId,
      'name': name,
      'type': type.name,
      'participantIds': participantIds,
      'createdBy': createdBy,
      'createdAt': createdAt.toIso8601String(),
      'lastMessageAt': lastMessageAt.toIso8601String(),
      'unreadCount': unreadCount,
      'lastMessage': lastMessage,
      'lastMessageSender': lastMessageSender,
    };
  }
  
  ChatRoom toEntity() {
    return ChatRoom(
      id: id,
      eventId: eventId,
      name: name,
      type: type,
      participantIds: participantIds,
      createdBy: createdBy,
      createdAt: createdAt,
      lastMessageAt: lastMessageAt,
      unreadCount: unreadCount,
      lastMessage: lastMessage,
      lastMessageSender: lastMessageSender,
    );
  }
}
