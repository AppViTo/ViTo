import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/invite_link.dart';
import '../repositories/event_repository.dart';

/// `createInviteLink` (Ola 1, T041): solo el organizador. Vence en
/// [CreateInviteLinkParams.expiresInDays] días (7 por defecto en el backend).
class CreateInviteLinkUseCase
    implements UseCase<InviteLink, CreateInviteLinkParams> {
  final EventRepository repository;

  CreateInviteLinkUseCase(this.repository);

  @override
  Future<Either<Failure, InviteLink>> call(CreateInviteLinkParams params) =>
      repository.createInviteLink(
        eventId: params.eventId,
        expiresInDays: params.expiresInDays,
        maxUses: params.maxUses,
      );
}

class CreateInviteLinkParams {
  final String eventId;

  /// De 1 a 30; `null` usa el valor por defecto del backend (7).
  final int? expiresInDays;

  /// De 1 a 1000; `null` es sin límite.
  final int? maxUses;

  const CreateInviteLinkParams({
    required this.eventId,
    this.expiresInDays,
    this.maxUses,
  });
}
