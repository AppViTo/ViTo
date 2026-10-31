import 'dart:typed_data';

import 'package:dartz/dartz.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/core/error/exceptions.dart';
import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/core/network/ws_connection.dart';
import 'package:vito_app/features/mosaic/data/datasources/mosaic_remote_datasource.dart';
import 'package:vito_app/features/mosaic/data/models/mosaic_model.dart';
import 'package:vito_app/features/mosaic/data/repositories/mosaic_repository_impl.dart';
import 'package:vito_app/features/mosaic/domain/entities/mosaic.dart';

import '../events/fakes.dart';

class _FakeMosaicRemoteDataSource implements MosaicRemoteDataSource {
  String? postedEventId;
  String? postedMediaId;
  String? postedContent;
  int postCalls = 0;
  Exception? postError;

  @override
  Future<MosaicFeedItemModel> addPost({
    required String eventId,
    required String mediaId,
    String? content,
  }) async {
    postCalls++;
    postedEventId = eventId;
    postedMediaId = mediaId;
    postedContent = content;
    if (postError != null) throw postError!;
    return MosaicFeedItemModel(
      id: 'mos_1',
      eventId: eventId,
      userId: 'usr_1',
      username: 'marta',
      displayName: 'Marta',
      type: FeedItemType.photo,
      content: content,
      mediaUrls: const ['https://cdn.vito.app/evt_1/foto.jpg'],
    );
  }

  @override
  Future<MosaicLikeStateModel> likePost(String itemId) async =>
      MosaicLikeStateModel(itemId: itemId, likesCount: 4, isLiked: true);

  @override
  Future<MosaicLikeStateModel> unlikePost(String itemId) async =>
      MosaicLikeStateModel(itemId: itemId, likesCount: 3, isLiked: false);

  @override
  Future<List<MosaicFeedItemModel>> getEventFeed({
    required String eventId,
    int page = 0,
    int size = 20,
  }) async =>
      [];

  @override
  Future<MosaicGridModel> getEventGrid(String eventId) async =>
      MosaicGridModel(eventId: eventId, grid: const []);

  @override
  Future<MosaicGridModel> initializeGrid({
    required String eventId,
    int rows = 4,
    int cols = 4,
  }) async =>
      MosaicGridModel(eventId: eventId, grid: const []);

  @override
  Future<MosaicFeedItemModel> getFeedItem(String itemId) =>
      throw UnimplementedError();

  @override
  Future<void> removeMosaicItem(String itemId) => throw UnimplementedError();
}

Failure _failureOf<T>(Either<Failure, T> result) =>
    result.fold((failure) => failure, (_) => throw StateError('era Right'));

void main() {
  late _FakeMosaicRemoteDataSource mosaic;
  late FakeMediaRemoteDataSource media;
  late MosaicRepositoryImpl repository;

  MosaicRepositoryImpl build(FakeMediaRemoteDataSource mediaDataSource) =>
      MosaicRepositoryImpl(
        remoteDataSource: mosaic,
        mediaDataSource: mediaDataSource,
        wsConnection: WsConnection(
          tokenProvider: () async => null,
          refreshAccessToken: () async => null,
        ),
      );

  setUp(() {
    mosaic = _FakeMosaicRemoteDataSource();
    media = FakeMediaRemoteDataSource();
    repository = build(media);
  });

  group('MosaicRepositoryImpl.uploadPhoto (ADR-016)', () {
    test('sube la imagen y publica con el mediaId devuelto', () async {
      final bytes = Uint8List.fromList([1, 2, 3]);

      final result = await repository.uploadPhoto(
        eventId: 'evt_1',
        bytes: bytes,
        fileName: 'foto.jpg',
        mimeType: 'image/jpeg',
        content: '¡Qué noche!',
      );

      expect(result.isRight(), isTrue);
      expect(media.capturedEventId, 'evt_1');
      expect(media.capturedMimeType, 'image/jpeg');
      expect(media.capturedBytes, bytes);
      expect(mosaic.postedEventId, 'evt_1');
      expect(mosaic.postedMediaId, 'med_1');
      expect(mosaic.postedContent, '¡Qué noche!');
    });

    for (final mimeType in ['video/mp4', 'image/heic', 'image/gif']) {
      test('rechaza $mimeType sin llamar a la API (excepción A3)', () async {
        final result = await repository.uploadPhoto(
          eventId: 'evt_1',
          bytes: Uint8List.fromList([1]),
          fileName: 'archivo',
          mimeType: mimeType,
        );

        expect(_failureOf(result), isA<ValidationFailure>());
        expect(media.capturedEventId, isNull);
        expect(mosaic.postCalls, 0);
      });
    }

    test('rechaza imágenes de más de 10 MB', () async {
      final result = await repository.uploadPhoto(
        eventId: 'evt_1',
        bytes: Uint8List(mosaicMaxUploadBytes + 1),
        fileName: 'enorme.png',
        mimeType: 'image/png',
      );

      expect(_failureOf(result), isA<ValidationFailure>());
      expect(mosaic.postCalls, 0);
    });

    test('si falla uploadMedia no publica en el mosaico', () async {
      repository = build(FakeMediaRemoteDataSource(
        uploadError: const ValidationException(message: 'El archivo supera los 10 MB'),
      ));

      final failure = _failureOf(await repository.uploadPhoto(
        eventId: 'evt_1',
        bytes: Uint8List.fromList([1]),
        fileName: 'foto.webp',
        mimeType: 'image/webp',
      ));

      expect(failure, isA<ValidationFailure>());
      expect(failure.message, 'El archivo supera los 10 MB');
      expect(mosaic.postCalls, 0);
    });

    test('403 de createMosaicPost es AuthorizationFailure', () async {
      mosaic.postError = const ServerException(
        message: 'Solo los asistentes confirmados pueden publicar en el mosaico',
        statusCode: 403,
      );

      final failure = _failureOf(await repository.uploadPhoto(
        eventId: 'evt_1',
        bytes: Uint8List.fromList([1]),
        fileName: 'foto.jpg',
        mimeType: 'image/jpeg',
      ));

      expect(failure, isA<AuthorizationFailure>());
      expect(failure.message,
          'Solo los asistentes confirmados pueden publicar en el mosaico');
    });
  });

  group('likePost / unlikePost', () {
    test('devuelven el MosaicLikeState de la respuesta', () async {
      final liked = await repository.likePost('mos_1');
      final unliked = await repository.unlikePost('mos_1');

      final likedState = liked.getOrElse(() => throw StateError('Left'));
      expect(likedState.itemId, 'mos_1');
      expect(likedState.likesCount, 4);
      expect(likedState.isLiked, isTrue);

      final unlikedState = unliked.getOrElse(() => throw StateError('Left'));
      expect(unlikedState.likesCount, 3);
      expect(unlikedState.isLiked, isFalse);
    });
  });
}
