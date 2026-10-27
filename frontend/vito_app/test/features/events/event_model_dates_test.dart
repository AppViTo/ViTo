import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/features/events/data/models/event_model.dart';

/// Bug-B01: las fechas llegan en UTC con `Z` (contrato) y se muestran en la hora del
/// dispositivo. Un evento de las 20:00 en Bogotá viaja como 01:00Z del día siguiente.
void main() {
  const Map<String, dynamic> json = {
    'id': 'evt_1',
    'organizerId': 'usr_1',
    'title': 'Cumpleaños',
    'status': 'PUBLISHED',
    'details': {
      'startDate': '2026-12-10T01:00:00Z',
      'endDate': '2026-12-10T07:00:00Z',
    },
    'createdAt': '2026-10-27T15:00:00Z',
  };

  test('las fechas leídas quedan en hora local con el mismo instante', () {
    final event = EventModel.fromJson(json);

    final start = event.details!.startDate!;
    expect(start.isUtc, isFalse);
    expect(start.toUtc(), DateTime.utc(2026, 12, 10, 1));
    expect(event.details!.endDate!.toUtc(), DateTime.utc(2026, 12, 10, 7));
    expect(event.createdAt!.isUtc, isFalse);
    expect(event.createdAt!.toUtc(), DateTime.utc(2026, 10, 27, 15));
  });

  test('al volver a enviarlas salen otra vez en UTC con Z', () {
    final out = EventModel.fromJson(json).toJson();

    expect(out['details']['startDate'], '2026-12-10T01:00:00.000Z');
    expect(out['details']['endDate'], '2026-12-10T07:00:00.000Z');
    expect(out['createdAt'], '2026-10-27T15:00:00.000Z');
  });
}
