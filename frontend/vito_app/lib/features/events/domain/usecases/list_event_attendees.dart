import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/attendee.dart';
import '../repositories/event_repository.dart';

/// `listEventAttendees` (Ola 1, T058): solo el organizador; nunca trae
/// correo ni teléfono. Paginado (ADR-008): una página por llamada.
class ListEventAttendeesUseCase
    implements UseCase<List<Attendee>, EventGuestsPageParams> {
  /// Tamaño de cada página: el máximo que admite el contrato (`size` ≤ 100).
  static const pageSize = 100;

  final EventRepository repository;

  ListEventAttendeesUseCase(this.repository);

  @override
  Future<Either<Failure, List<Attendee>>> call(EventGuestsPageParams params) =>
      repository.getEventAttendees(
        eventId: params.eventId,
        page: params.page,
        size: pageSize,
      );
}

/// Una página (base 0) de una lista de invitados de [eventId].
class EventGuestsPageParams {
  final String eventId;
  final int page;

  const EventGuestsPageParams({required this.eventId, this.page = 0});
}
