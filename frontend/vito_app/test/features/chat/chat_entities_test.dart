import 'package:flutter_test/flutter_test.dart';
import 'package:vito_app/features/chat/domain/entities/chat_message.dart';
import 'package:vito_app/features/chat/domain/entities/chat_room.dart';

void main() {
  group('ChatMessage', () {
    test('should create ChatMessage from JSON', () {
      final json = {
        'id': '123',
        'roomId': 'room1',
        'senderId': 'user1',
        'senderName': 'John',
        'content': 'Hello World',
        'type': 'TEXT',
        'timestamp': '2024-01-01T00:00:00.000Z',
        'deleted': false,
      };

      final message = ChatMessage.fromJson(json);

      expect(message.id, '123');
      expect(message.roomId, 'room1');
      expect(message.senderId, 'user1');
      expect(message.senderName, 'John');
      expect(message.content, 'Hello World');
      expect(message.type, MessageType.TEXT);
      expect(message.deleted, false);
    });

    test('should convert ChatMessage to JSON', () {
      final message = ChatMessage(
        id: '123',
        roomId: 'room1',
        senderId: 'user1',
        senderName: 'John',
        content: 'Hello World',
        type: MessageType.TEXT,
        timestamp: DateTime(2024, 1, 1),
        deleted: false,
      );

      final json = message.toJson();

      expect(json['id'], '123');
      expect(json['roomId'], 'room1');
      expect(json['senderId'], 'user1');
      expect(json['senderName'], 'John');
      expect(json['content'], 'Hello World');
      expect(json['type'], 'TEXT');
      expect(json['deleted'], false);
    });

    test('should copy with new values', () {
      final message = ChatMessage(
        id: '123',
        roomId: 'room1',
        senderId: 'user1',
        senderName: 'John',
        content: 'Hello',
        type: MessageType.TEXT,
        timestamp: DateTime(2024, 1, 1),
        deleted: false,
      );

      final updated = message.copyWith(content: 'Updated message');

      expect(updated.content, 'Updated message');
      expect(updated.id, '123');
      expect(updated.senderName, 'John');
    });
  });

  group('ChatRoom', () {
    test('should create ChatRoom from JSON', () {
      final json = {
        'id': 'room123',
        'eventId': 'event1',
        'name': 'Event Chat',
        'type': 'EVENT_CHAT',
        'participantIds': ['user1', 'user2'],
        'createdBy': 'user1',
        'createdAt': '2024-01-01T00:00:00.000Z',
        'lastMessageAt': '2024-01-01T12:00:00.000Z',
        'unreadCount': 5,
      };

      final room = ChatRoom.fromJson(json);

      expect(room.id, 'room123');
      expect(room.eventId, 'event1');
      expect(room.name, 'Event Chat');
      expect(room.type, RoomType.EVENT_CHAT);
      expect(room.participantIds.length, 2);
      expect(room.unreadCount, 5);
    });

    test('should convert ChatRoom to JSON', () {
      final room = ChatRoom(
        id: 'room123',
        eventId: 'event1',
        name: 'Event Chat',
        type: RoomType.EVENT_CHAT,
        participantIds: const ['user1', 'user2'],
        createdBy: 'user1',
        createdAt: DateTime(2024, 1, 1),
        lastMessageAt: DateTime(2024, 1, 1, 12),
        unreadCount: 5,
      );

      final json = room.toJson();

      expect(json['id'], 'room123');
      expect(json['eventId'], 'event1');
      expect(json['name'], 'Event Chat');
      expect(json['type'], 'EVENT_CHAT');
      expect(json['participantIds'].length, 2);
      expect(json['unreadCount'], 5);
    });
  });
}
