import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../repositories/event_repository.dart';

/// `cancelEvent` (Ola 1, T055): pasa el evento a `CANCELLED` sin borrarlo.
/// Es idempotente; un evento ya terminado responde `ConflictFailure`.
class CancelEventUseCase implements UseCase<void, String> {
  final EventRepository repository;

  CancelEventUseCase(this.repository);

  /// [eventId] del evento.
  @override
  Future<Either<Failure, void>> call(String eventId) =>
      repository.cancelEvent(eventId);
}
