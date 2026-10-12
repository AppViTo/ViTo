import 'package:flutter/foundation.dart';
import 'dart:async';

import 'package:dartz/dartz.dart';
import 'package:flutter/foundation.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../../../../core/error/exceptions.dart';
import '../../../../core/error/failures.dart';
import '../datasources/mosaic_remote_datasource.dart';
import '../models/mosaic_model.dart';
import '../../domain/entities/mosaic.dart';
import '../../domain/repositories/mosaic_repository.dart';

class MosaicRepositoryImpl implements MosaicRepository {
  final MosaicRemoteDataSource remoteDataSource;
  
  final _feedController = StreamController<MosaicFeedItem>.broadcast();
  final _gridController = StreamController<MosaicGrid>.broadcast();
  final _likeController = StreamController<int>.broadcast();
  
  WebSocketChannel? _webSocketChannel;
  
  MosaicRepositoryImpl({required this.remoteDataSource});
  
  @override
  Future<Either<Failure, MosaicFeedItem>> addPost({
    required String eventId,
    required String content,
  }) async {
    try {
      final item = await remoteDataSource.addPost(
        eventId: eventId,
        content: content,
      );
      return Right(item);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, MosaicFeedItem>> addPhoto({
    required String eventId,
    String? content,
    required List<String> mediaUrls,
  }) async {
    try {
      final item = await remoteDataSource.addPhoto(
        eventId: eventId,
        content: content,
        mediaUrls: mediaUrls,
      );
      return Right(item);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, MosaicFeedItem>> addCheckIn({
    required String eventId,
    String? location,
  }) async {
    try {
      final item = await remoteDataSource.addCheckIn(
        eventId: eventId,
        location: location,
      );
      return Right(item);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
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
  Future<Either<Failure, void>> likePost(String itemId) async {
    try {
      await remoteDataSource.likePost(itemId);
      return const Right(null);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, void>> unlikePost(String itemId) async {
    try {
      await remoteDataSource.unlikePost(itemId);
      return const Right(null);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
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
  void connectToWebSocket(String eventId, String baseUrl) {
    final wsUrl = '${baseUrl.replaceFirst('http', 'ws')}/ws/mosaic/$eventId';
    _webSocketChannel = WebSocketChannel.connect(Uri.parse(wsUrl));
    
    _webSocketChannel!.stream.listen(
      (message) {
        final data = Map<String, dynamic>.from(message as Map);
        
        if (data['type'] == 'NEW_ITEM') {
          final item = MosaicFeedItemModel.fromJson(data['data']);
          _feedController.add(item);
        } else if (data['type'] == 'GRID_UPDATE') {
          // Update grid logic here
        } else if (data['type'] == 'LIKE_UPDATE') {
          final likesCount = data['data']['likesCount'] as int;
          _likeController.add(likesCount);
        }
      },
      onError: (error) {
        debugPrint('WebSocket error: $error');
      },
      onDone: () {
        debugPrint('WebSocket connection closed');
      },
    );
  }
  
  @override
  void disconnectFromWebSocket() {
    _webSocketChannel?.sink.close();
    _webSocketChannel = null;
  }
  
  void dispose() {
    disconnectFromWebSocket();
    _feedController.close();
    _gridController.close();
    _likeController.close();
  }
}
