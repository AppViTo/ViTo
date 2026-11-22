import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/rsvp.dart';
import '../repositories/event_repository.dart';

/// `respondRsvp` (Ola 1, T032): responde con `RespondRsvpRequest` y recibe
/// `Rsvp`. Es la única forma de aceptar o rechazar una invitación: el backend
/// actualiza la invitación en la misma petición (FR-017).
class RespondRSVPUseCase implements UseCase<RSVP, RespondRSVPParams> {
  final EventRepository repository;

  RespondRSVPUseCase(this.repository);

  @override
  Future<Either<Failure, RSVP>> call(RespondRSVPParams params) async {
    return await repository.respondToRSVP(
      eventId: params.eventId,
      status: params.status,
      message: params.message,
    );
  }
}

class RespondRSVPParams {
  final String eventId;

  /// `accepted`, `declined` o `maybe` (`RespondRsvpRequest.status`).
  final RSVPStatus status;

  /// Opcional, hasta 280 caracteres.
  final String? message;

  RespondRSVPParams({
    required this.eventId,
    required this.status,
    this.message,
  }) : assert(
          respondableRsvpStatuses.contains(status),
          'Solo se puede responder accepted, declined o maybe',
        );
}
