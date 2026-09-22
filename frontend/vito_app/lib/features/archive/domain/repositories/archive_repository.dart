import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../entities/archive.dart';

abstract class ArchiveRepository {
  Future<Either<Failure, EventArchive>> createArchive({
    required String eventId,
    required String title,
    String? description,
  });
  
  Future<Either<Failure, EventArchive>> getArchive(String archiveId);
  
  Future<Either<Failure, EventArchive>> getArchiveByEventId(String eventId);
  
  Future<Either<Failure, List<EventArchive>>> getPublicArchives({
    int page = 0,
    int size = 20,
  });
  
  Future<Either<Failure, List<EventArchive>>> getUserArchives({
    int page = 0,
    int size = 20,
  });
  
  Future<Either<Failure, EventArchive>> updateArchive({
    required String archiveId,
    String? title,
    String? description,
    String? coverUrl,
    bool? isPublic,
    bool? allowComments,
    bool? allowReactions,
    bool? allowSharing,
  });
  
  Future<Either<Failure, ArchiveMedia>> uploadMedia({
    required String archiveId,
    required MediaType type,
    required String url,
    String? thumbnailUrl,
    String? caption,
    MediaMetadata? metadata,
  });
  
  Future<Either<Failure, List<ArchiveMedia>>> getMedia({
    required String archiveId,
    int page = 0,
    int size = 50,
  });
  
  Future<Either<Failure, ArchiveMedia>> getMediaById(String mediaId);
  
  Future<Either<Failure, ArchiveComment>> addComment({
    required String archiveId,
    required String content,
    String? mediaId,
    String? parentId,
  });
  
  Future<Either<Failure, List<ArchiveComment>>> getComments({
    required String archiveId,
    String? mediaId,
    int page = 0,
    int size = 50,
  });
  
  Future<Either<Failure, void>> updateComment({
    required String commentId,
    required String content,
  });
  
  Future<Either<Failure, void>> deleteComment(String commentId);
  
  Future<Either<Failure, void>> likeMedia(String mediaId);
  
  Future<Either<Failure, void>> unlikeMedia(String mediaId);
}
