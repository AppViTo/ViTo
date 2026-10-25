import 'package:flutter/foundation.dart';
import 'dart:async';

import 'package:dartz/dartz.dart';
import '../../../../core/error/exceptions.dart';
import '../../../../core/network/ws_connection.dart';
import '../../../../core/error/failures.dart';
import '../datasources/checkin_remote_datasource.dart';
import '../models/checkin_event_model.dart';
import '../../domain/entities/checkin_ticket.dart';
import '../../domain/entities/checkin_event.dart';
import '../../domain/repositories/checkin_repository.dart';

class CheckInRepositoryImpl implements CheckInRepository {
  final CheckInRemoteDataSource remoteDataSource;
  
  final _checkInController = StreamController<CheckInEvent>.broadcast();
  final _statsController = StreamController<CheckInStats>.broadcast();
  
  final WsConnection wsConnection;
  
  CheckInRepositoryImpl({
    required this.remoteDataSource,
    required this.wsConnection,
  });
  
  @override
  Future<Either<Failure, CheckInTicket>> generateTicket(String eventId) async {
    try {
      final ticket = await remoteDataSource.generateTicket(eventId);
      return Right(ticket);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, CheckInResult>> validateAndCheckIn({
    required String qrCode,
    required String eventId,
    String? location,
    double? latitude,
    double? longitude,
  }) async {
    try {
      final result = await remoteDataSource.validateAndCheckIn(
        qrCode: qrCode,
        eventId: eventId,
        location: location,
        latitude: latitude,
        longitude: longitude,
      );
      
      return Right(CheckInResult(
        success: result.success,
        message: result.message,
        ticket: result.ticket,
        event: result.event,
      ));
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, CheckInTicket>> getTicket(String ticketId) async {
    try {
      final ticket = await remoteDataSource.getTicket(ticketId);
      return Right(ticket);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, List<CheckInTicket>>> getEventTickets({
    required String eventId,
    int page = 0,
    int size = 20,
  }) async {
    try {
      final tickets = await remoteDataSource.getEventTickets(
        eventId: eventId,
        page: page,
        size: size,
      );
      return Right(tickets);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, List<CheckInEvent>>> getRecentCheckIns({
    required String eventId,
    int limit = 10,
  }) async {
    try {
      final checkIns = await remoteDataSource.getRecentCheckIns(
        eventId: eventId,
        limit: limit,
      );
      return Right(checkIns);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, CheckInStats>> getEventStats(String eventId) async {
    try {
      final stats = await remoteDataSource.getEventStats(eventId);
      return Right(stats);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, void>> revokeTicket(String ticketId) async {
    try {
      await remoteDataSource.revokeTicket(ticketId);
      return const Right(null);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Stream<CheckInEvent> get checkInStream => _checkInController.stream;
  
  @override
  Stream<CheckInStats> get statsStream => _statsController.stream;
  
  @override
  void connectToWebSocket(String eventId) {
    unawaited(wsConnection.connect(
      '/ws/checkin/$eventId',
      onData: _handleSocketMessage,
      onError: (error) => debugPrint('WebSocket error: $error'),
    ));
  }
  
  void _handleSocketMessage(dynamic message) {
    final data = Map<String, dynamic>.from(
      Map<String, dynamic>.from(message as Map),
    );
    
    if (data['type'] == 'CHECK_IN') {
      final checkInEvent = CheckInEventModel.fromJson(data['data']);
      _checkInController.add(checkInEvent);
    } else if (data['type'] == 'STATS_UPDATE') {
      final stats = CheckInStatsModel.fromJson(data['data']);
      _statsController.add(stats);
    }
  }
  
  @override
  void disconnectFromWebSocket() {
    unawaited(wsConnection.disconnect());
  }
  
  void dispose() {
    disconnectFromWebSocket();
    _checkInController.close();
    _statsController.close();
  }
}
