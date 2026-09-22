import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/rsvp.dart';
import '../repositories/event_repository.dart';

class RespondRSVPUseCase implements UseCase<RSVP, RespondRSVPParams> {
  final EventRepository repository;
  
  RespondRSVPUseCase(this.repository);
  
  @override
  Future<Either<Failure, RSVP>> call(RespondRSVPParams params) async {
    return await repository.respondToRSVP(
      eventId: params.eventId,
      status: params.status,
      message: params.message,
      guestsCount: params.guestsCount,
    );
  }
}

class RespondRSVPParams {
  final String eventId;
  final RSVPStatus status;
  final String? message;
  final int guestsCount;
  
  RespondRSVPParams({
    required this.eventId,
    required this.status,
    this.message,
    this.guestsCount = 0,
  });
}
