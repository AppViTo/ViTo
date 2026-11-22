import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../repositories/event_repository.dart';

/// `revokeInviteLink` (Ola 1, T041): idempotente. Quien ya se unió conserva
/// su invitación; el enlace deja de admitir a nadie más.
class RevokeInviteLinkUseCase implements UseCase<void, RevokeInviteLinkParams> {
  final EventRepository repository;

  RevokeInviteLinkUseCase(this.repository);

  @override
  Future<Either<Failure, void>> call(RevokeInviteLinkParams params) =>
      repository.revokeInviteLink(
        eventId: params.eventId,
        linkId: params.linkId,
      );
}

class RevokeInviteLinkParams {
  final String eventId;
  final String linkId;

  const RevokeInviteLinkParams({required this.eventId, required this.linkId});
}
