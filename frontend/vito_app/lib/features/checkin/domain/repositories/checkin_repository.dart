import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../entities/checkin_ticket.dart';
import '../entities/checkin_event.dart';

abstract class CheckInRepository {
  Future<Either<Failure, CheckInTicket>> generateTicket(String eventId);
  
  Future<Either<Failure, CheckInResult>> validateAndCheckIn({
    required String qrCode,
    required String eventId,
    String? location,
    double? latitude,
    double? longitude,
  });
  
  Future<Either<Failure, CheckInTicket>> getTicket(String ticketId);
  
  Future<Either<Failure, List<CheckInTicket>>> getEventTickets({
    required String eventId,
    int page = 0,
    int size = 20,
  });
  
  Future<Either<Failure, List<CheckInEvent>>> getRecentCheckIns({
    required String eventId,
    int limit = 10,
  });
  
  Future<Either<Failure, CheckInStats>> getEventStats(String eventId);
  
  Future<Either<Failure, void>> revokeTicket(String ticketId);
  
  Stream<CheckInEvent> get checkInStream;
  Stream<CheckInStats> get statsStream;
  
  void connectToWebSocket(String eventId);
  void disconnectFromWebSocket();
}

class CheckInResult {
  final bool success;
  final String message;
  final CheckInTicket? ticket;
  final CheckInEvent? event;
  
  CheckInResult({
    required this.success,
    required this.message,
    this.ticket,
    this.event,
  });
}
