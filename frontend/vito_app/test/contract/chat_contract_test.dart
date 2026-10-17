import 'package:flutter_test/flutter_test.dart';
import 'package:vito_app/features/chat/data/models/chat_message_model.dart';
import 'package:vito_app/features/chat/data/models/chat_room_model.dart';

import 'fixture_loader.dart';

/// T-C04 — Serialización del contrato de chat.
///
/// Fuentes: `docs/api/fixtures/chat/*.2xx.json` (T-C01), enums de
/// CONVENTIONS §6 (`ChatRoom.type`, `ChatMessage.type`) y array plano de
/// ADR-002.
void main() {
  const roomTypes = {'EVENT_CHAT', 'PRIVATE', 'GROUP'};
  const messageTypes = {
    'TEXT',
    'IMAGE',
    'VIDEO',
    'AUDIO',
    'FILE',
    'SYSTEM',
    'TYPING',
    'READ_RECEIPT',
  };

  final roomFixtures = <String>{
    'chat/createChatRoom.201.json',
    'chat/joinChatRoom.200.json',
  };

  void expectValidRoom(Map<String, dynamic> json, String origin) {
    expect(json['id'], isA<String>());
    expect((json['id'] as String).isNotEmpty, isTrue, reason: origin);
    expect(json['eventId'], isA<String>(), reason: origin);
    expect(roomTypes.contains(json['type']), isTrue,
        reason: '$origin: "${json['type']}" fuera del enum ChatRoom.type');
    expect(json['participantIds'], isA<List>());
    expect(
      (json['participantIds'] as List).every((e) => e is String),
      isTrue,
      reason: '$origin: participantIds es una lista de ids',
    );
    expect(DateTime.parse(json['createdAt'] as String).isUtc, isTrue,
        reason: '$origin: createdAt ISO-8601 UTC con Z');
    expect(json['unreadCount'], isA<int>(), reason: origin);
  }

  void expectValidMessage(Map<String, dynamic> json, String origin) {
    expect(json['id'], isA<String>());
    expect(json['roomId'], isA<String>(), reason: origin);
    expect(json['senderId'], isA<String>(), reason: origin);
    expect(json['content'], isA<String>(), reason: origin);
    expect(messageTypes.contains(json['type']), isTrue,
        reason: '$origin: "${json['type']}" fuera del enum ChatMessage.type');
    expect(DateTime.parse(json['timestamp'] as String).isUtc, isTrue,
        reason: '$origin: timestamp ISO-8601 UTC con Z');
    expect(json['deleted'], isA<bool>(), reason: origin);
    if (json.containsKey('mediaUrl') && json['mediaUrl'] != null) {
      expect(json['mediaUrl'], isA<String>(), reason: origin);
    }
  }

  group('ChatRoom (CONVENTIONS §6)', () {
    for (final path in roomFixtures) {
      test('$path cumple el enum de tipo y los ids de participantes', () {
        final json = loadFixtureObject(path);
        expectValidRoom(json, path);

        final model = ChatRoomModel.fromJson(json);
        expect(model.id, json['id']);
        expect(model.eventId, json['eventId']);
        expect(model.type.name, json['type']);
        expect(model.participantIds, List<String>.from(json['participantIds']));

        final roundTrip = ChatRoomModel.fromJson(model.toJson());
        expect(roundTrip.id, model.id);
        expect(roundTrip.type, model.type);
        expect(roundTrip.participantIds, model.participantIds);
        expect(roundTrip.unreadCount, model.unreadCount);
      });
    }

    test('createChatRoom.201.json usa el tipo de ejemplo del contrato', () {
      final json = loadFixtureObject('chat/createChatRoom.201.json');
      expect(json['type'], 'EVENT_CHAT');
      expect(json['name'], 'Chat de Los 30 de Marta');
    });
  });

  group('ChatMessage (CONVENTIONS §6)', () {
    test('sendChatMessage.201.json parsea y hace round-trip', () {
      final json = loadFixtureObject('chat/sendChatMessage.201.json');
      expectValidMessage(json, 'chat/sendChatMessage.201.json');

      final model = ChatMessageModel.fromJson(json);
      expect(model.id, json['id']);
      expect(model.roomId, json['roomId']);
      expect(model.type.name, json['type']);
      expect(model.content, json['content']);
      expect(model.deleted, json['deleted']);

      final roundTrip = ChatMessageModel.fromJson(model.toJson());
      expect(roundTrip.id, model.id);
      expect(roundTrip.type, model.type);
      expect(roundTrip.content, model.content);
      expect(roundTrip.deleted, model.deleted);
    });

    test('listChatMessages.200.json es un array plano (ADR-002)', () {
      final list = loadFixtureList('chat/listChatMessages.200.json');

      expect(list, isNotEmpty);
      expect(
        list.any((item) => item is Map && item.containsKey('total')),
        isFalse,
        reason: 'ADR-008: no existe "total" en las listas',
      );

      for (final item in list) {
        expect(item, isA<Map<String, dynamic>>());
        final map = item as Map<String, dynamic>;
        expectValidMessage(map, 'chat/listChatMessages.200.json');
        expect(ChatMessageModel.fromJson(map).id, map['id']);
      }
    });

    test('el segundo mensaje ejercita el caso con mediaUrl (campo opcional)',
        () {
      final list = loadFixtureList('chat/listChatMessages.200.json');
      final withMedia = list[1] as Map<String, dynamic>;

      expect(withMedia['type'], 'IMAGE');
      expect(withMedia['mediaUrl'], isA<String>());
      expect(
        ChatMessageModel.fromJson(withMedia).mediaUrl,
        withMedia['mediaUrl'],
      );
    });
  });
}
