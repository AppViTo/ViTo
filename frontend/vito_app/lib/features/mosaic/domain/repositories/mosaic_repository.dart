import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../entities/mosaic.dart';

abstract class MosaicRepository {
  Future<Either<Failure, MosaicFeedItem>> addPost({
    required String eventId,
    required String content,
  });
  
  Future<Either<Failure, MosaicFeedItem>> addPhoto({
    required String eventId,
    String? content,
    required List<String> mediaUrls,
  });
  
  Future<Either<Failure, MosaicFeedItem>> addCheckIn({
    required String eventId,
    String? location,
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
  
  Future<Either<Failure, void>> likePost(String itemId);
  
  Future<Either<Failure, void>> unlikePost(String itemId);
  
  Future<Either<Failure, MosaicFeedItem>> getFeedItem(String itemId);
  
  Stream<MosaicFeedItem> get feedStream;
  Stream<MosaicGrid> get gridStream;
  Stream<int> get likeStream;
  
  void connectToWebSocket(String eventId);
  void disconnectFromWebSocket();
}
