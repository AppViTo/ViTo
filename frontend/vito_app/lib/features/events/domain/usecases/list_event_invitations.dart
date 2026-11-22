import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/invitation.dart';
import '../repositories/event_repository.dart';

/// `listEventInvitations` (Ola 1, T052): solo el organizador.
class ListEventInvitationsUseCase implements UseCase<List<Invitation>, String> {
  final EventRepository repository;

  ListEventInvitationsUseCase(this.repository);

  /// [eventId] del evento.
  @override
  Future<Either<Failure, List<Invitation>>> call(String eventId) =>
      repository.getEventInvitations(eventId: eventId, size: 100);
}
