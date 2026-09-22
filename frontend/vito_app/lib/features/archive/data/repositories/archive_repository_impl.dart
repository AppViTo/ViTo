import 'package:dartz/dartz.dart';

import '../../../../core/error/exceptions.dart';
import '../../../../core/error/failures.dart';
import '../datasources/archive_remote_datasource.dart';
import '../../domain/entities/archive.dart';
import '../../domain/repositories/archive_repository.dart';

class ArchiveRepositoryImpl implements ArchiveRepository {
  final ArchiveRemoteDataSource remoteDataSource;
  
  ArchiveRepositoryImpl({required this.remoteDataSource});
  
  @override
  Future<Either<Failure, EventArchive>> createArchive({
    required String eventId,
    required String title,
    String? description,
  }) async {
    try {
      final archive = await remoteDataSource.createArchive(
        eventId: eventId,
        title: title,
        description: description,
      );
      return Right(archive);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, EventArchive>> getArchive(String archiveId) async {
    try {
      final archive = await remoteDataSource.getArchive(archiveId);
      return Right(archive);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, EventArchive>> getArchiveByEventId(String eventId) async {
    try {
      final archive = await remoteDataSource.getArchiveByEventId(eventId);
      return Right(archive);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, List<EventArchive>>> getPublicArchives({
    int page = 0,
    int size = 20,
  }) async {
    try {
      final archives = await remoteDataSource.getPublicArchives(page: page, size: size);
      return Right(archives);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, List<EventArchive>>> getUserArchives({
    int page = 0,
    int size = 20,
  }) async {
    try {
      final archives = await remoteDataSource.getUserArchives(page: page, size: size);
      return Right(archives);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, EventArchive>> updateArchive({
    required String archiveId,
    String? title,
    String? description,
    String? coverUrl,
    bool? isPublic,
    bool? allowComments,
    bool? allowReactions,
    bool? allowSharing,
  }) async {
    try {
      final archive = await remoteDataSource.updateArchive(
        archiveId: archiveId,
        title: title,
        description: description,
        coverUrl: coverUrl,
        isPublic: isPublic,
        allowComments: allowComments,
        allowReactions: allowReactions,
        allowSharing: allowSharing,
      );
      return Right(archive);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, ArchiveMedia>> uploadMedia({
    required String archiveId,
    required MediaType type,
    required String url,
    String? thumbnailUrl,
    String? caption,
    MediaMetadata? metadata,
  }) async {
    try {
      final media = await remoteDataSource.uploadMedia(
        archiveId: archiveId,
        type: type.name.toUpperCase(),
        url: url,
        thumbnailUrl: thumbnailUrl,
        caption: caption,
        metadata: metadata != null ? {
          'fileSize': metadata.fileSize,
          'width': metadata.width,
          'height': metadata.height,
          'duration': metadata.duration,
          'mimeType': metadata.mimeType,
          'location': metadata.location,
        } : null,
      );
      return Right(media);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, List<ArchiveMedia>>> getMedia({
    required String archiveId,
    int page = 0,
    int size = 50,
  }) async {
    try {
      final media = await remoteDataSource.getMedia(archiveId: archiveId, page: page, size: size);
      return Right(media);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, ArchiveMedia>> getMediaById(String mediaId) async {
    try {
      final media = await remoteDataSource.getMediaById(mediaId);
      return Right(media);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, ArchiveComment>> addComment({
    required String archiveId,
    required String content,
    String? mediaId,
    String? parentId,
  }) async {
    try {
      final comment = await remoteDataSource.addComment(
        archiveId: archiveId,
        content: content,
        mediaId: mediaId,
        parentId: parentId,
      );
      return Right(comment);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, List<ArchiveComment>>> getComments({
    required String archiveId,
    String? mediaId,
    int page = 0,
    int size = 50,
  }) async {
    try {
      final comments = await remoteDataSource.getComments(
        archiveId: archiveId,
        mediaId: mediaId,
        page: page,
        size: size,
      );
      return Right(comments);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, void>> updateComment({
    required String commentId,
    required String content,
  }) async {
    try {
      await remoteDataSource.updateComment(commentId: commentId, content: content);
      return const Right(null);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, void>> deleteComment(String commentId) async {
    try {
      await remoteDataSource.deleteComment(commentId);
      return const Right(null);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, void>> likeMedia(String mediaId) async {
    try {
      await remoteDataSource.likeMedia(mediaId);
      return const Right(null);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, void>> unlikeMedia(String mediaId) async {
    try {
      await remoteDataSource.unlikeMedia(mediaId);
      return const Right(null);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
}
