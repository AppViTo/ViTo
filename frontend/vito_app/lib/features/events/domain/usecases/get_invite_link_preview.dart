import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/invite_link.dart';
import '../repositories/event_repository.dart';

/// `getInviteLinkPreview` (Ola 1, T041): público, sin sesión. Un token
/// inexistente, vencido o revocado responde `NotFoundFailure`.
class GetInviteLinkPreviewUseCase
    implements UseCase<InviteLinkPreview, String> {
  final EventRepository repository;

  GetInviteLinkPreviewUseCase(this.repository);

  /// [token] del enlace (22 caracteres base64url).
  @override
  Future<Either<Failure, InviteLinkPreview>> call(String token) =>
      repository.getInviteLinkPreview(token);
}
