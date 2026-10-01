import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/features/events/presentation/bloc/event_bloc.dart';

import 'fakes.dart';

void main() {
  late FakeEventRepository repository;
  late EventBloc bloc;

  setUp(() {
    repository = FakeEventRepository();
    bloc = EventBloc(eventRepository: repository);
  });

  tearDown(() async {
    await bloc.close();
  });

  test('UploadEventCover emite uploading y uploaded con la url', () async {
    final expectation = expectLater(
      bloc.stream,
      emitsInOrder(<Object>[
        isA<EventCoverUploading>(),
        isA<EventCoverUploaded>(),
      ]),
    );

    bloc.add(UploadEventCover(
      eventId: 'evt_1',
      bytes: _bytes,
      fileName: 'portada.jpg',
      mimeType: 'image/jpeg',
    ));

    await expectation;

    expect(repository.uploadedCovers, hasLength(1));
    expect(repository.uploadedCovers.single.$1, 'evt_1');
    expect(repository.uploadedCovers.single.$2, 'portada.jpg');
    expect(repository.uploadedCovers.single.$3, 'image/jpeg');
  });

  test('UploadEventCover emite uploadFailed conservando el mensaje', () async {
    final failing = FakeEventRepository(failCoverUpload: true);
    final failingBloc = EventBloc(eventRepository: failing);

    final expectation = expectLater(
      failingBloc.stream,
      emitsInOrder(<Object>[
        isA<EventCoverUploading>(),
        isA<EventCoverUploadFailed>()
            .having((s) => s.message, 'message', 'Sin conexión'),
      ]),
    );

    failingBloc.add(UploadEventCover(
      eventId: 'evt_1',
      bytes: _bytes,
      fileName: 'portada.jpg',
      mimeType: 'image/jpeg',
    ));

    await expectation;
    expect(failing.uploadedCovers, isEmpty);
    await failingBloc.close();
  });

  test('CreateEvent emite EventCreated con el evento devuelto', () async {
    final expectation = expectLater(
      bloc.stream,
      emitsInOrder(<Object>[
        isA<EventLoading>(),
        isA<EventCreated>().having((s) => s.event.id, 'id', 'evt_1'),
      ]),
    );

    bloc.add(const CreateEvent(title: 'Los 30 de Marta'));

    await expectation;
    expect(repository.created.single.title, 'Los 30 de Marta');
  });
}

final Uint8List _bytes = Uint8List.fromList([1, 2, 3]);
