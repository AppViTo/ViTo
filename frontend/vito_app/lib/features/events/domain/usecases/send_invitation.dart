import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/invitation.dart';
import '../repositories/event_repository.dart';

/// `sendInvitation` (Ola 1, T052): el organizador invita a un usuario
/// registrado; la invitación nace `PENDING` y vence en 7 días. Buscar a quién
/// invitar llega con la Ola 2a (`searchUsers`, T-F13).
class SendInvitationUseCase implements UseCase<Invitation, SendInvitationParams> {
  final EventRepository repository;

  SendInvitationUseCase(this.repository);

  @override
  Future<Either<Failure, Invitation>> call(SendInvitationParams params) =>
      repository.sendInvitation(
        eventId: params.eventId,
        inviteeId: params.inviteeId,
        message: params.message,
      );
}

class SendInvitationParams {
  final String eventId;
  final String inviteeId;

  /// Opcional, hasta 280 caracteres.
  final String? message;

  const SendInvitationParams({
    required this.eventId,
    required this.inviteeId,
    this.message,
  });
}
