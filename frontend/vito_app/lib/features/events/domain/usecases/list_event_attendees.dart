import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/attendee.dart';
import '../repositories/event_repository.dart';

/// `listEventAttendees` (Ola 1, T058): solo el organizador; nunca trae
/// correo ni teléfono.
class ListEventAttendeesUseCase implements UseCase<List<Attendee>, String> {
  final EventRepository repository;

  ListEventAttendeesUseCase(this.repository);

  /// [eventId] del evento.
  @override
  Future<Either<Failure, List<Attendee>>> call(String eventId) =>
      repository.getEventAttendees(eventId: eventId, size: 100);
}
