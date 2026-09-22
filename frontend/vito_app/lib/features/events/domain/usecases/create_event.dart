import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/event.dart';
import '../repositories/event_repository.dart';

class CreateEventUseCase implements UseCase<Event, CreateEventParams> {
  final EventRepository repository;
  
  CreateEventUseCase(this.repository);
  
  @override
  Future<Either<Failure, Event>> call(CreateEventParams params) async {
    return await repository.createEvent(
      title: params.title,
      description: params.description,
      details: params.details,
      settings: params.settings,
    );
  }
}

class CreateEventParams {
  final String title;
  final String? description;
  final EventDetails? details;
  final EventSettings? settings;
  
  CreateEventParams({
    required this.title,
    this.description,
    this.details,
    this.settings,
  });
}
