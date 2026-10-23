import 'package:flutter_test/flutter_test.dart';
import 'package:vito_app/features/events/data/models/event_model.dart';

import 'fixture_loader.dart';

/// T-C04 — Serialización del contrato de eventos.
///
/// Fuentes: `docs/api/fixtures/events/*.2xx.json` (T-C01), estructura anidada
/// de ADR-001, array plano de ADR-002 y enums de CONVENTIONS §6.
void main() {
  /// Campos planos que ADR-001 eliminó de la raíz del Evento.
  const flatFieldsForbiddenAtRoot = <String>[
    'name',
    'date',
    'endDate',
    'location',
    'address',
    'isPublic',
    'maxAttendees',
    'currentAttendees',
    'category',
  ];

  const eventStatuses = {'DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED'};
  const categories = {
    'BIRTHDAY',
    'MEETING',
    'WEDDING',
    'GRADUATION',
    'TRAVEL',
    'PARTY',
  };

  final objectFixtures = <String>{
    'events/createEvent.201.json',
    'events/getEvent.200.json',
  };

  void expectValidEvent(Map<String, dynamic> json, String origin) {
    for (final key in flatFieldsForbiddenAtRoot) {
      expect(
        json.containsKey(key),
        isFalse,
        reason: '$origin: ADR-001 prohíbe "$key" en la raíz; vive en '
            'details/settings/stats',
      );
    }

    expect(json['id'], isA<String>(), reason: '$origin: id obligatorio');
    expect((json['id'] as String).startsWith('evt_'), isTrue,
        reason: '$origin: CONVENTIONS §6 exige prefijo evt_');
    expect((json['organizerId'] as String).startsWith('usr_'), isTrue,
        reason: '$origin: CONVENTIONS §6 exige prefijo usr_');
    expect(json['title'], isA<String>());
    expect((json['title'] as String).isNotEmpty, isTrue);

    expect(eventStatuses.contains(json['status']), isTrue,
        reason: '$origin: "${json['status']}" fuera del enum Event.status '
            'de CONVENTIONS §6');

    final details = json['details'];
    if (details != null) {
      expect(details, isA<Map<String, dynamic>>());
      final map = details as Map<String, dynamic>;
      expect(categories.contains(map['category']), isTrue,
          reason: '$origin: "${map['category']}" fuera del enum category');
      for (final field in ['startDate', 'endDate']) {
        final raw = map[field];
        if (raw != null) {
          final date = DateTime.parse(raw as String);
          expect(date.isUtc, isTrue,
              reason: '$origin: $field debe ser ISO-8601 UTC con Z');
        }
      }
      if (map['startDate'] != null && map['endDate'] != null) {
        expect(
          DateTime.parse(map['endDate'] as String).isAfter(
            DateTime.parse(map['startDate'] as String),
          ),
          isTrue,
          reason: '$origin: endDate debe ser estrictamente posterior a startDate',
        );
      }
    }

    final settings = json['settings'];
    if (settings != null) {
      expect(settings, isA<Map<String, dynamic>>());
      expect(
        (settings as Map<String, dynamic>)['isPublic'],
        isA<bool>(),
        reason: '$origin: isPublic es booleano, nunca string',
      );
    }

    final stats = json['stats'];
    if (stats != null) {
      expect(stats, isA<Map<String, dynamic>>());
      for (final entry in (stats as Map<String, dynamic>).entries) {
        expect(entry.value, isA<int>(), reason: '$origin: stats.$entry');
      }
    }

    for (final field in ['createdAt', 'updatedAt']) {
      final raw = json[field];
      if (raw != null) {
        expect(DateTime.parse(raw as String).isUtc, isTrue,
            reason: '$origin: $field debe ser ISO-8601 UTC con Z');
      }
    }
  }

  group('Event (ADR-001)', () {
    for (final path in objectFixtures) {
      test('$path cumple la estructura anidada y los enums', () {
        final json = loadFixtureObject(path);
        expectValidEvent(json, path);

        final model = EventModel.fromJson(json);
        expect(model.id, json['id']);
        expect(model.title, json['title']);
        expect(model.status.name.toUpperCase(), json['status']);

        final roundTrip = EventModel.fromJson(model.toJson());
        expect(roundTrip.id, model.id);
        expect(roundTrip.title, model.title);
        expect(roundTrip.status, model.status);
        expect(roundTrip.details?.category, model.details?.category);
        expect(roundTrip.settings?.maxAttendees, model.settings?.maxAttendees);
        expect(roundTrip.stats?.totalAccepted, model.stats?.totalAccepted);
      });
    }

    test('getEvent.200.json mantiene el evento de ejemplo de CONVENTIONS', () {
      final json = loadFixtureObject('events/getEvent.200.json');
      expect(json['title'], 'Boda Andrea & Luis');
      expect(json['details']['category'], 'WEDDING');
      expect(json['settings']['hideAddressUntilAccepted'], false);
      expect(json['details']['latitude'], 4.711);
      expect(json['details']['longitude'], -74.0721);
    });

    test('listEvents.mine.200.json es un array plano de eventos (ADR-002)', () {
      final list = loadFixtureList('events/listEvents.mine.200.json');

      expect(list, isNotEmpty);
      expect(
        list.any((item) => item is Map && item.containsKey('events')),
        isFalse,
        reason: 'ADR-002: no existe envelope {events: [...]}',
      );
      expect(
        list.any((item) => item is Map && item.containsKey('total')),
        isFalse,
        reason: 'ADR-008: no existe "total" en las respuestas',
      );

      for (final item in list) {
        expect(item, isA<Map<String, dynamic>>());
        final map = item as Map<String, dynamic>;
        expectValidEvent(map, 'events/listEvents.mine.200.json');
        expect(EventModel.fromJson(map).id, map['id']);
      }
    });

    test('los tres fixtures de evento comparten el mismo organizador', () {
      final created = loadFixtureObject('events/createEvent.201.json');
      final detail = loadFixtureObject('events/getEvent.200.json');
      final listed =
          (loadFixtureList('events/listEvents.mine.200.json').first as Map)
              .cast<String, dynamic>();

      expect(created['organizerId'], detail['organizerId']);
      expect(created['organizerId'], listed['organizerId']);
    });
  });
}
