// Los valores SCREAMING_CASE de los enums son el wire format de
// docs/api/openapi.yaml y se serializan con `.name`; renombrarlos rompería el contrato.
// ignore_for_file: constant_identifier_names

import 'package:equatable/equatable.dart';

class ChatMessage extends Equatable {
  final String id;
  final String roomId;
  final String senderId;
  final String senderName;
  final String content;
  final MessageType type;
  final String? mediaUrl;
  final DateTime timestamp;
  final DateTime? editedAt;
  final bool deleted;
  final bool isOwnMessage;
  
  const ChatMessage({
    required this.id,
    required this.roomId,
    required this.senderId,
    required this.senderName,
    required this.content,
    required this.type,
    this.mediaUrl,
    required this.timestamp,
    this.editedAt,
    this.deleted = false,
    this.isOwnMessage = false,
  });
  
  factory ChatMessage.fromJson(Map<String, dynamic> json) {
    return ChatMessage(
      id: json['id'] ?? '',
      roomId: json['roomId'] ?? '',
      senderId: json['senderId'] ?? '',
      senderName: json['senderName'] ?? '',
      content: json['content'] ?? '',
      type: MessageType.values.firstWhere(
        (e) => e.name == json['type'],
        orElse: () => MessageType.TEXT,
      ),
      mediaUrl: json['mediaUrl'],
      timestamp: json['timestamp'] != null 
          ? DateTime.parse(json['timestamp']) 
          : DateTime.now(),
      editedAt: json['editedAt'] != null 
          ? DateTime.parse(json['editedAt']) 
          : null,
      deleted: json['deleted'] ?? false,
    );
  }
  
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'roomId': roomId,
      'senderId': senderId,
      'senderName': senderName,
      'content': content,
      'type': type.name,
      'mediaUrl': mediaUrl,
      'timestamp': timestamp.toIso8601String(),
      'editedAt': editedAt?.toIso8601String(),
      'deleted': deleted,
    };
  }
  
  @override
  List<Object?> get props => [
    id, roomId, senderId, senderName, content, type,
    mediaUrl, timestamp, editedAt, deleted, isOwnMessage
  ];
  
  ChatMessage copyWith({
    String? id,
    String? roomId,
    String? senderId,
    String? senderName,
    String? content,
    MessageType? type,
    String? mediaUrl,
    DateTime? timestamp,
    DateTime? editedAt,
    bool? deleted,
    bool? isOwnMessage,
  }) {
    return ChatMessage(
      id: id ?? this.id,
      roomId: roomId ?? this.roomId,
      senderId: senderId ?? this.senderId,
      senderName: senderName ?? this.senderName,
      content: content ?? this.content,
      type: type ?? this.type,
      mediaUrl: mediaUrl ?? this.mediaUrl,
      timestamp: timestamp ?? this.timestamp,
      editedAt: editedAt ?? this.editedAt,
      deleted: deleted ?? this.deleted,
      isOwnMessage: isOwnMessage ?? this.isOwnMessage,
    );
  }
}

enum MessageType {
  TEXT,
  IMAGE,
  VIDEO,
  AUDIO,
  FILE,
  SYSTEM,
  TYPING,
  READ_RECEIPT
}
