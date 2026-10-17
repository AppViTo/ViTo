import 'package:flutter_test/flutter_test.dart';

import 'fixture_loader.dart';

/// T-C04 — Contrato de media.
///
/// ⚠️ El código todavía **no tiene** `MediaModel`: `uploadEventCover` en
/// `lib/features/events/data/datasources/media_remote_datasource.dart` lee el
/// body a mano (`location.url`). Este test valida por eso el **subconjunto que
/// el datasource consume**, más los invariantes de CONVENTIONS §6 y §8.
///
/// Cuando exista `MediaModel` (tarea propuesta en el PR de T-C04) hay que
/// sustituir estas aserciones por un `fromJson`/`toJson` real, igual que en el
/// resto de archivos de esta carpeta.
void main() {
  const statuses = {'UPLOADING', 'UPLOADED', 'PROCESSING', 'PROCESSED', 'FAILED'};
  const fixtures = <String>[
    'media/uploadMedia.201.json',
    'media/getMedia.200.json',
  ];

  group('Media (CONVENTIONS §8)', () {
    for (final path in fixtures) {
      test('$path expone la URL que consume el datasource', () {
        final json = loadFixtureObject(path);

        expect(json['id'], isA<String>());
        expect((json['id'] as String).isNotEmpty, isTrue, reason: path);
        expect(json['eventId'], isA<String>(), reason: path);
        expect(json['uploadedBy'], isA<String>(), reason: path);

        expect(json['fileSize'], isA<int>(), reason: path);
        expect(json['fileSize'] as int, greaterThan(0));
        expect(json['contentType'], isA<String>(), reason: path);

        expect(statuses.contains(json['status']), isTrue,
            reason: '$path: "${json['status']}" fuera del enum Media.status '
                'de CONVENTIONS §6');

        // Subconjunto que lee MediaRemoteDataSourceImpl.uploadEventCover.
        final location = json['location'];
        expect(location, isA<Map<String, dynamic>>(),
            reason: '$path: el datasource lee location.url');
        final url = (location as Map<String, dynamic>)['url'];
        expect(url, isA<String>(), reason: '$path: location.url es String');
        expect(url as String, startsWith('https://'),
            reason: '$path: la URL del CDN debe ser https');
        expect(location['key'], isA<String>(), reason: path);
        expect(location['bucket'], isA<String>(), reason: path);
      });
    }

    test('uploadMedia.201.json mantiene la portada de ejemplo', () {
      final json = loadFixtureObject('media/uploadMedia.201.json');
      expect(json['fileName'], 'portada.jpg');
      expect(json['contentType'], 'image/jpeg');
      expect(json['status'], 'UPLOADED');
      expect(json['uploadedAt'], isA<String>());
      expect(DateTime.parse(json['uploadedAt'] as String).isUtc, isTrue);
    });

    test('los dos fixtures describen el mismo archivo', () {
      final uploaded = loadFixtureObject('media/uploadMedia.201.json');
      final fetched = loadFixtureObject('media/getMedia.200.json');

      expect(fetched['id'], uploaded['id']);
      expect(fetched['eventId'], uploaded['eventId']);
      expect(fetched['fileSize'], uploaded['fileSize']);
      expect(
        (fetched['location'] as Map)['url'],
        (uploaded['location'] as Map)['url'],
      );
    });
  });
}
