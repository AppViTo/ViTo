import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/core/error/exceptions.dart';
import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/features/events/data/repositories/event_repository_impl.dart';
import 'package:vito_app/features/events/domain/entities/event.dart';

import 'fakes.dart';

void main() {
  late FakeEventRemoteDataSource remote;
  late FakeMediaRemoteDataSource media;
  late EventRepositoryImpl repository;

  setUp(() {
    remote = FakeEventRemoteDataSource();
    media = FakeMediaRemoteDataSource();
    repository = EventRepositoryImpl(
      remoteDataSource: remote,
      mediaDataSource: media,
    );
  });

  Failure? failureOf(dynamic result) => result.fold((f) => f, (_) => null);

  group('EventRepositoryImpl.createEvent', () {
    test('serializa EventDetails plano sin lanzar TypeError (ADR-001)',
        () async {
      final details = EventDetails(
        startDate: DateTime.utc(2026, 8, 22, 20, 0),
        endDate: DateTime.utc(2026, 8, 23, 2, 0),
        location: 'Casa de Marta',
        address: 'Calle 10 # 45-67',
        latitude: 4.7110,
        longitude: -74.0721,
        tags: const ['cumple'],
        category: 'BIRTHDAY',
      );

      final result =
          await repository.createEvent(title: 'Los 30 de Marta', details: details);

      expect(result.isRight(), isTrue);
      expect(remote.capturedDetails, isNotNull);
      expect(remote.capturedTitle, 'Los 30 de Marta');
      expect(remote.capturedDetails!['category'], 'BIRTHDAY');
      expect(remote.capturedDetails!['address'], 'Calle 10 # 45-67');
      expect(remote.capturedDetails!['location'], 'Casa de Marta');
      expect(remote.capturedDetails!['latitude'], 4.7110);
      expect(remote.capturedDetails!['longitude'], -74.0721);
      expect(remote.capturedDetails!['tags'], ['cumple']);
      expect(remote.capturedDetails!['startDate'], '2026-08-22T20:00:00.000Z');
      expect(remote.capturedDetails!['endDate'], '2026-08-23T02:00:00.000Z');
      expect(remote.capturedDetails!['coverImages'], isNull);
    });

    test('convierte fechas locales a UTC con sufijo Z (CONVENTIONS §6)',
        () async {
      final details = EventDetails(
        startDate: DateTime(2026, 8, 22, 20, 0),
        endDate: DateTime(2026, 8, 23, 2, 0),
      );

      await repository.createEvent(title: 'Local', details: details);

      final start = remote.capturedDetails!['startDate'] as String;
      expect(start, endsWith('Z'));
      expect(DateTime.parse(start).toLocal(), DateTime(2026, 8, 22, 20, 0));
    });

    test('serializa EventSettings plano a JSON', () async {
      const settings = EventSettings(
        isPublic: false,
        requiresApproval: true,
        maxAttendees: 30,
        allowGuestInvites: false,
        enableLiveStreaming: false,
        enableMosaic: false,
        hideAddressUntilAccepted: true,
      );

      final result = await repository.createEvent(title: 'Boda', settings: settings);

      expect(result.isRight(), isTrue);
      expect(remote.capturedSettings, {
        'isPublic': false,
        'requiresApproval': true,
        'maxAttendees': 30,
        'allowGuestInvites': false,
        'enableLiveStreaming': false,
        'enableMosaic': false,
        'hideAddressUntilAccepted': true,
      });
    });

    test('omite details y settings cuando son nulos', () async {
      await repository.createEvent(title: 'Sin extras');

      expect(remote.capturedDetails, isNull);
      expect(remote.capturedSettings, isNull);
    });

    test('convierte ValidationException en ValidationFailure con su mensaje',
        () async {
      final failing = FakeEventRemoteDataSource(
        createError: const ValidationException(
          message: 'El campo title es obligatorio',
        ),
      );
      final repo = EventRepositoryImpl(
        remoteDataSource: failing,
        mediaDataSource: media,
      );

      final failure = failureOf(await repo.createEvent(title: ''));

      expect(failure, isA<ValidationFailure>());
      expect(failure!.message, 'El campo title es obligatorio');
    });

    test('convierte NetworkException en NetworkFailure', () async {
      final failing = FakeEventRemoteDataSource(
        createError: const NetworkException(
          message: 'Network error. Please try again.',
        ),
      );
      final repo = EventRepositoryImpl(
        remoteDataSource: failing,
        mediaDataSource: media,
      );

      final failure = failureOf(await repo.createEvent(title: 'x'));

      expect(failure, isA<NetworkFailure>());
      expect(failure!.message, 'Network error. Please try again.');
    });
  });

  group('EventRepositoryImpl.uploadEventCover', () {
    test('delega en MediaRemoteDataSource con el payload recibido', () async {
      final bytes = Uint8List.fromList([1, 2, 3, 4]);

      final result = await repository.uploadEventCover(
        eventId: 'evt_1',
        bytes: bytes,
        fileName: 'portada.jpg',
        mimeType: 'image/jpeg',
      );

      expect(result.isRight(), isTrue);
      expect(media.capturedEventId, 'evt_1');
      expect(media.capturedFileName, 'portada.jpg');
      expect(media.capturedMimeType, 'image/jpeg');
      expect(media.capturedBytes, bytes);
      expect(result.getOrElse(() => ''), 'https://cdn.vito.app/cover.jpg');
    });

    test('propaga el mensaje de error del backend', () async {
      final failing = FakeMediaRemoteDataSource(
        uploadError: const ServerException(
          message: 'El archivo supera los 10 MB',
          statusCode: 400,
        ),
      );
      final repo = EventRepositoryImpl(
        remoteDataSource: remote,
        mediaDataSource: failing,
      );

      final failure = failureOf(await repo.uploadEventCover(
        eventId: 'evt_1',
        bytes: Uint8List.fromList([1]),
        fileName: 'grande.jpg',
        mimeType: 'image/jpeg',
      ));

      expect(failure, isA<ServerFailure>());
      expect(failure!.message, 'El archivo supera los 10 MB');
    });
  });
}
