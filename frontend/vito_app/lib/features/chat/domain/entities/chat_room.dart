// Los nombres de los enums se serializan tal cual (`.name`) y deben coincidir
// con los valores en MAYÚSCULAS del contrato (docs/api). No renombrar.
// ignore_for_file: constant_identifier_names

import 'package:equatable/equatable.dart';

class ChatRoom extends Equatable {
  final String id;
  final String eventId;
  final String? name;
  final RoomType type;
  final List<String> participantIds;
  final String createdBy;
  final DateTime createdAt;
  final DateTime lastMessageAt;
  final int unreadCount;
  final String? lastMessage;
  final String? lastMessageSender;
  
  const ChatRoom({
    required this.id,
    required this.eventId,
    this.name,
    required this.type,
    required this.participantIds,
    required this.createdBy,
    required this.createdAt,
    required this.lastMessageAt,
    this.unreadCount = 0,
    this.lastMessage,
    this.lastMessageSender,
  });
  
  factory ChatRoom.fromJson(Map<String, dynamic> json) {
    return ChatRoom(
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
  
  @override
  List<Object?> get props => [
    id, eventId, name, type, participantIds, createdBy,
    createdAt, lastMessageAt, unreadCount, lastMessage, lastMessageSender
  ];
  
  ChatRoom copyWith({
    String? id,
    String? eventId,
    String? name,
    RoomType? type,
    List<String>? participantIds,
    String? createdBy,
    DateTime? createdAt,
    DateTime? lastMessageAt,
    int? unreadCount,
    String? lastMessage,
    String? lastMessageSender,
  }) {
    return ChatRoom(
      id: id ?? this.id,
      eventId: eventId ?? this.eventId,
      name: name ?? this.name,
      type: type ?? this.type,
      participantIds: participantIds ?? this.participantIds,
      createdBy: createdBy ?? this.createdBy,
      createdAt: createdAt ?? this.createdAt,
      lastMessageAt: lastMessageAt ?? this.lastMessageAt,
      unreadCount: unreadCount ?? this.unreadCount,
      lastMessage: lastMessage ?? this.lastMessage,
      lastMessageSender: lastMessageSender ?? this.lastMessageSender,
    );
  }
}

enum RoomType {
  EVENT_CHAT,
  PRIVATE,
  GROUP
}
