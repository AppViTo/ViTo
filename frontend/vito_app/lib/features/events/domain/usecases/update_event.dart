import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/event.dart';
import '../repositories/event_repository.dart';

/// `updateEvent` (Ola 1, T048): el organizador edita su evento y la app envía
/// solo los cambios. Cierra la parte de app de Bug-F07.
class UpdateEventUseCase implements UseCase<Event, UpdateEventParams> {
  final EventRepository repository;

  UpdateEventUseCase(this.repository);

  @override
  Future<Either<Failure, Event>> call(UpdateEventParams params) =>
      repository.updateEvent(original: params.original, edited: params.edited);
}

class UpdateEventParams {
  /// El evento tal como lo devolvió el backend.
  final Event original;

  /// El mismo evento con los cambios del formulario.
  final Event edited;

  const UpdateEventParams({required this.original, required this.edited});
}
