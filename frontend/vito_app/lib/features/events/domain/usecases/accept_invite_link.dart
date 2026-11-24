import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/invitation.dart';
import '../repositories/event_repository.dart';

/// `acceptInviteLink` (Ola 1, T041): exige sesión y es idempotente. Un
/// enlace sin usos o un evento que ya no admite invitados responden
/// `ConflictFailure`.
class AcceptInviteLinkUseCase implements UseCase<Invitation, String> {
  final EventRepository repository;

  AcceptInviteLinkUseCase(this.repository);

  /// [token] del enlace.
  @override
  Future<Either<Failure, Invitation>> call(String token) =>
      repository.acceptInviteLink(token);
}
