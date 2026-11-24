import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/invitation.dart';
import '../repositories/event_repository.dart';
import 'list_event_attendees.dart';

export 'list_event_attendees.dart' show EventGuestsPageParams;

/// `listEventInvitations` (Ola 1, T052): solo el organizador. Paginado
/// (ADR-008): una página por llamada.
class ListEventInvitationsUseCase
    implements UseCase<List<Invitation>, EventGuestsPageParams> {
  /// Tamaño de cada página: el máximo que admite el contrato (`size` ≤ 100).
  static const pageSize = 100;

  final EventRepository repository;

  ListEventInvitationsUseCase(this.repository);

  @override
  Future<Either<Failure, List<Invitation>>> call(
    EventGuestsPageParams params,
  ) =>
      repository.getEventInvitations(
        eventId: params.eventId,
        page: params.page,
        size: pageSize,
      );
}
