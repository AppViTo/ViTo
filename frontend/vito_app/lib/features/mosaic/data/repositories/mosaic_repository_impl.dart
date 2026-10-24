import 'package:flutter/foundation.dart';
import 'dart:async';

import 'package:dartz/dartz.dart';
import '../../../../core/error/exceptions.dart';
import '../../../../core/network/ws_connection.dart';
import '../../../../core/error/failures.dart';
import '../../../events/data/datasources/media_remote_datasource.dart';
import '../datasources/mosaic_remote_datasource.dart';
import '../models/mosaic_model.dart';
import '../../domain/entities/mosaic.dart';
import '../../domain/repositories/mosaic_repository.dart';

class MosaicRepositoryImpl implements MosaicRepository {
  final MosaicRemoteDataSource remoteDataSource;

  /// Paso 1 de la subida (`uploadMedia`), el mismo que usa la portada.
  final MediaRemoteDataSource mediaDataSource;

  final _feedController = StreamController<MosaicFeedItem>.broadcast();
  final _gridController = StreamController<MosaicGrid>.broadcast();
  final _likeController = StreamController<int>.broadcast();
  
  final WsConnection wsConnection;
  
  MosaicRepositoryImpl({
    required this.remoteDataSource,
    required this.mediaDataSource,
    required this.wsConnection,
  });

  Failure _toFailure(Object error) {
    if (error is ValidationException) {
      return ValidationFailure(message: error.message);
    }
    if (error is AuthenticationException) {
      return AuthenticationFailure(message: error.message);
    }
    if (error is NetworkException) return NetworkFailure(message: error.message);
    if (error is ServerException) {
      if (error.statusCode == 403) {
        return AuthorizationFailure(message: error.message);
      }
      if (error.statusCode == 404) {
        return NotFoundFailure(message: error.message);
      }
      return ServerFailure(message: error.message);
    }
    return ServerFailure(message: error.toString());
  }

  @override
  Future<Either<Failure, MosaicFeedItem>> uploadPhoto({
    required String eventId,
    required Uint8List bytes,
    required String fileName,
    required String mimeType,
    String? content,
  }) async {
    if (!mosaicAllowedImageTypes.contains(mimeType)) {
      return const Left(ValidationFailure(
        message: 'Solo se pueden publicar imágenes JPEG, PNG o WebP',
      ));
    }
    if (bytes.lengthInBytes > mosaicMaxUploadBytes) {
      return const Left(ValidationFailure(
        message: 'La foto supera los 10 MB',
      ));
    }

    try {
      final mediaId = await mediaDataSource.uploadMedia(
        eventId: eventId,
        bytes: bytes,
        fileName: fileName,
        mimeType: mimeType,
      );
      if (mediaId.isEmpty) {
        return const Left(ServerFailure(
          message: 'No se pudo subir la foto. Inténtalo de nuevo.',
        ));
      }

      final item = await remoteDataSource.addPost(
        eventId: eventId,
        mediaId: mediaId,
        content: content,
      );
      return Right(item);
    } catch (e) {
      return Left(_toFailure(e));
    }
  }
  
  @override
  Future<Either<Failure, List<MosaicFeedItem>>> getEventFeed({
    required String eventId,
    int page = 0,
    int size = 20,
  }) async {
    try {
      final feed = await remoteDataSource.getEventFeed(
        eventId: eventId,
        page: page,
        size: size,
      );
      return Right(feed);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, MosaicGrid>> getEventGrid(String eventId) async {
    try {
      final grid = await remoteDataSource.getEventGrid(eventId);
      return Right(grid);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, MosaicGrid>> initializeGrid({
    required String eventId,
    int rows = 4,
    int cols = 4,
  }) async {
    try {
      final grid = await remoteDataSource.initializeGrid(
        eventId: eventId,
        rows: rows,
        cols: cols,
      );
      return Right(grid);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, MosaicLikeState>> likePost(String itemId) async {
    try {
      return Right(await remoteDataSource.likePost(itemId));
    } catch (e) {
      return Left(_toFailure(e));
    }
  }

  @override
  Future<Either<Failure, MosaicLikeState>> unlikePost(String itemId) async {
    try {
      return Right(await remoteDataSource.unlikePost(itemId));
    } catch (e) {
      return Left(_toFailure(e));
    }
  }
  
  @override
  Future<Either<Failure, MosaicFeedItem>> getFeedItem(String itemId) async {
    try {
      final item = await remoteDataSource.getFeedItem(itemId);
      return Right(item);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Stream<MosaicFeedItem> get feedStream => _feedController.stream;
  
  @override
  Stream<MosaicGrid> get gridStream => _gridController.stream;
  
  @override
  Stream<int> get likeStream => _likeController.stream;
  
  @override
  void connectToWebSocket(String eventId) {
    unawaited(wsConnection.connect(
      '/ws/mosaic/$eventId',
      onData: _handleSocketMessage,
      onError: (error) => debugPrint('WebSocket error: $error'),
    ));
  }
  
  void _handleSocketMessage(dynamic message) {
    final data = Map<String, dynamic>.from(message as Map);
    
    if (data['type'] == 'NEW_ITEM') {
      final item = MosaicFeedItemModel.fromJson(data['data']);
      _feedController.add(item);
    } else if (data['type'] == 'LIKE_UPDATE') {
      final likesCount = data['data']['likesCount'] as int;
      _likeController.add(likesCount);
    }
  }
  
  @override
  void disconnectFromWebSocket() {
    unawaited(wsConnection.disconnect());
  }
  
  void dispose() {
    disconnectFromWebSocket();
    _feedController.close();
    _gridController.close();
    _likeController.close();
  }
}
