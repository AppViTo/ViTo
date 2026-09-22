import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/checkin_ticket.dart';
import '../repositories/checkin_repository.dart';

class GenerateTicketUseCase implements UseCase<CheckInTicket, String> {
  final CheckInRepository repository;
  
  GenerateTicketUseCase(this.repository);
  
  @override
  Future<Either<Failure, CheckInTicket>> call(String eventId) async {
    return await repository.generateTicket(eventId);
  }
}
