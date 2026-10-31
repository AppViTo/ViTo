import 'package:flutter/foundation.dart';
import 'dart:async';
import 'dart:convert';

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
  final _likeController = StreamController<MosaicLikeCountUpdate>.broadcast();
  final _itemRemovedController = StreamController<String>.broadcast();
  
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
  Future<Either<Failure, void>> removeMosaicItem(String itemId) async {
    try {
      await remoteDataSource.removeMosaicItem(itemId);
      return const Right(null);
    } catch (e) {
      return Left(_toFailure(e));
    }
  }

  @override
  Stream<MosaicFeedItem> get feedStream => _feedController.stream;
  
  @override
  Stream<MosaicGrid> get gridStream => _gridController.stream;
  
  @override
  Stream<MosaicLikeCountUpdate> get likeStream => _likeController.stream;

  @override
  Stream<String> get itemRemovedStream => _itemRemovedController.stream;
  
  @override
  void connectToWebSocket(String eventId) {
    unawaited(wsConnection.connect(
      '/ws/mosaic/$eventId',
      onData: _handleSocketMessage,
      onError: (error) => debugPrint('WebSocket error: $error'),
    ));
  }
  
  /// Frames del canal `/ws/mosaic/{eventId}` según asyncapi.yaml: son planos,
  /// sin envoltorio `data`. Un frame mal formado se descarta.
  void _handleSocketMessage(dynamic message) {
    final data = _decodeFrame(message);
    if (data == null) return;

    switch (data['type']) {
      case 'NEW_ITEM':
        // `MosaicNewItem`: `{type, item: MosaicFeedItem}`.
        final item = data['item'];
        if (item is Map<String, dynamic>) {
          _feedController.add(MosaicFeedItemModel.fromJson(item));
        }
      case 'GRID_UPDATE':
        // `MosaicGridUpdate`: `{type, eventId, grid}`.
        if (data['grid'] is List) {
          final grid = MosaicGridModel.fromJson(data);
          _gridController.add(MosaicGridModel(
            eventId: grid.eventId,
            grid: grid.grid,
            totalItems:
                grid.grid.expand((row) => row).whereType<MosaicItem>().length,
          ));
        }
      case 'LIKE_UPDATE':
        // `MosaicLikeUpdate`: `{type, itemId, likesCount}`.
        final likedId = data['itemId'];
        final likesCount = data['likesCount'];
        if (likedId is String && likedId.isNotEmpty && likesCount is int) {
          _likeController.add(
            MosaicLikeCountUpdate(itemId: likedId, likesCount: likesCount),
          );
        }
      case 'ITEM_REMOVED':
        // `MosaicItemRemoved`: `{type, eventId, itemId}`.
        final removedId = data['itemId'];
        if (removedId is String && removedId.isNotEmpty) {
          _itemRemovedController.add(removedId);
        }
    }
  }

  /// web_socket_channel entrega el frame como texto JSON.
  Map<String, dynamic>? _decodeFrame(dynamic message) {
    try {
      final decoded = message is String ? jsonDecode(message) : message;
      return decoded is Map ? Map<String, dynamic>.from(decoded) : null;
    } on FormatException {
      return null;
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
    _itemRemovedController.close();
  }
}
