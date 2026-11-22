import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/invite_link.dart';
import '../repositories/event_repository.dart';

/// `listInviteLinks` (Ola 1, T041): solo el organizador.
class ListInviteLinksUseCase implements UseCase<List<InviteLink>, String> {
  final EventRepository repository;

  ListInviteLinksUseCase(this.repository);

  /// [eventId] del evento.
  @override
  Future<Either<Failure, List<InviteLink>>> call(String eventId) =>
      repository.listInviteLinks(eventId: eventId);
}
