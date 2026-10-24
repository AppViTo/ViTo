import 'dart:typed_data';

import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../entities/mosaic.dart';

abstract class MosaicRepository {
  /// Subida en dos pasos (ADR-016): `uploadMedia` y luego `createMosaicPost`
  /// con el `mediaId`. Solo [mosaicAllowedImageTypes] (excepción A3).
  Future<Either<Failure, MosaicFeedItem>> uploadPhoto({
    required String eventId,
    required Uint8List bytes,
    required String fileName,
    required String mimeType,
    String? content,
  });

  Future<Either<Failure, List<MosaicFeedItem>>> getEventFeed({
    required String eventId,
    int page = 0,
    int size = 20,
  });
  
  Future<Either<Failure, MosaicGrid>> getEventGrid(String eventId);
  
  Future<Either<Failure, MosaicGrid>> initializeGrid({
    required String eventId,
    int rows = 4,
    int cols = 4,
  });
  
  Future<Either<Failure, MosaicLikeState>> likePost(String itemId);

  Future<Either<Failure, MosaicLikeState>> unlikePost(String itemId);
  
  Future<Either<Failure, MosaicFeedItem>> getFeedItem(String itemId);
  
  Stream<MosaicFeedItem> get feedStream;
  Stream<MosaicGrid> get gridStream;
  Stream<int> get likeStream;
  
  void connectToWebSocket(String eventId);
  void disconnectFromWebSocket();
}
