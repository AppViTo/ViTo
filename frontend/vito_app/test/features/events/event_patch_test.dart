import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/features/events/data/models/event_model.dart';
import 'package:vito_app/features/events/data/models/event_patch.dart';
import 'package:vito_app/features/events/data/repositories/event_repository_impl.dart';
import 'package:vito_app/features/events/domain/entities/event.dart';

import '../../contract/fixture_loader.dart';
import 'fakes.dart';

Event _base() => Event(
      id: 'evt_1',
      organizerId: 'usr_1',
      title: 'Boda Andrea & Luis',
      description: 'Ceremonia',
      status: EventStatus.published,
      details: EventDetails(
        startDate: DateTime.utc(2026, 12, 12, 23),
        endDate: DateTime.utc(2026, 12, 13, 7),
        location: 'Hacienda El Retiro',
        address: 'Km 5 vía Chía',
        coverImages: const ['https://cdn.vito.com/a.jpg'],
        category: 'WEDDING',
      ),
      settings: const EventSettings(maxAttendees: 150, isPublic: false),
      stats: const EventStats(totalAccepted: 96),
    );

Event _with(
  Event e, {
  String? title,
  String? description,
  bool clearDescription = false,
  EventDetails? details,
  EventSettings? settings,
}) =>
    Event(
      id: e.id,
      organizerId: e.organizerId,
      title: title ?? e.title,
      description: clearDescription ? null : (description ?? e.description),
      details: details ?? e.details,
      settings: settings ?? e.settings,
      stats: e.stats,
      status: e.status,
    );

/// T048: `updateEvent` envía solo lo que cambió (merge-patch).
void main() {
  test('sin cambios no hay parche', () {
    expect(eventPatchJson(_base(), _base()), isEmpty);
  });

  test('solo título, descripción y cupo, como updateEvent.request.json', () {
    final original = _base();
    final edited = _with(
      original,
      title: 'Boda Andrea & Luis — ceremonia y fiesta',
      description: 'Ceremonia en la hacienda, fiesta hasta el amanecer',
      settings: const EventSettings(maxAttendees: 160, isPublic: false),
    );

    expect(
      eventPatchJson(original, edited),
      loadFixtureObject('events/updateEvent.request.json'),
    );
  });

  test('en details solo van las claves que cambian, con fechas en UTC', () {
    final original = _base();
    final edited = _with(
      original,
      details: EventDetails(
        startDate: DateTime.utc(2026, 12, 13, 0),
        endDate: original.details!.endDate,
        location: original.details!.location,
        address: original.details!.address,
        coverImages: const ['https://cdn.vito.com/b.jpg'],
        category: 'WEDDING',
      ),
    );

    expect(eventPatchJson(original, edited), {
      'details': {
        'startDate': '2026-12-13T00:00:00.000Z',
        'coverImages': ['https://cdn.vito.com/b.jpg'],
      },
    });
  });

  test('vaciar la descripción o la dirección envía null para borrarlas', () {
    final original = _base();
    final edited = _with(
      original,
      clearDescription: true,
      details: EventDetails(
        startDate: original.details!.startDate,
        endDate: original.details!.endDate,
        location: original.details!.location,
        coverImages: original.details!.coverImages,
        category: 'WEDDING',
      ),
    );

    expect(eventPatchJson(original, edited), {
      'description': null,
      'details': {'address': null},
    });
  });

  test('nunca envía campos no editables', () {
    final patch = eventPatchJson(
      _base(),
      _with(_base(), settings: const EventSettings(isPublic: true, maxAttendees: 150)),
    );
    for (final key in const ['id', 'organizerId', 'status', 'stats', 'createdAt']) {
      expect(patch.containsKey(key), isFalse, reason: key);
    }
    expect(patch, {
      'settings': {'isPublic': true},
    });
  });

  group('EventRepositoryImpl.updateEvent', () {
    test('envía el parche al datasource', () async {
      final remote = FakeEventRemoteDataSource();
      final repo = EventRepositoryImpl(
        remoteDataSource: remote,
        mediaDataSource: FakeMediaRemoteDataSource(),
      );

      await repo.updateEvent(
        original: _base(),
        edited: _with(_base(), title: 'Boda de Andrea'),
      );

      expect(remote.capturedPatch, {'title': 'Boda de Andrea'});
    });

    test('sin cambios no llama al backend y devuelve el original', () async {
      final remote = FakeEventRemoteDataSource();
      final repo = EventRepositoryImpl(
        remoteDataSource: remote,
        mediaDataSource: FakeMediaRemoteDataSource(),
      );

      final result = await repo.updateEvent(original: _base(), edited: _base());

      expect(remote.capturedPatch, isNull);
      expect(result.getOrElse(() => throw StateError('falló')), _base());
    });
  });

  test('la respuesta updateEvent.200.json se lee como Event', () {
    final event =
        EventModel.fromJson(loadFixtureObject('events/updateEvent.200.json'));
    expect(event.id, startsWith('evt_'));
    expect(event.settings?.maxAttendees, isNotNull);
  });
}
