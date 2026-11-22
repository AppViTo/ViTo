import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../../core/error/failures.dart';
import '../../domain/entities/invitation.dart';
import '../../domain/entities/invite_link.dart';
import '../../domain/usecases/accept_invite_link.dart';
import '../../domain/usecases/get_invite_link_preview.dart';

/// Mensaje único para un enlace inexistente, vencido o revocado (FR-006):
/// no se distingue el motivo.
const inviteLinkUnavailableMessage =
    'El enlace no existe o ya no está disponible';

enum InviteLinkStatus { loading, preview, unavailable, failure, joined }

/// Página `/i/<token>` (T043): vista previa pública y unión con sesión.
class InviteLinkState extends Equatable {
  final InviteLinkStatus status;
  final InviteLinkPreview? preview;
  final Invitation? invitation;
  final bool joining;

  /// Error de carga o de la última acción.
  final String? error;

  const InviteLinkState({
    this.status = InviteLinkStatus.loading,
    this.preview,
    this.invitation,
    this.joining = false,
    this.error,
  });

  @override
  List<Object?> get props => [status, preview, invitation, joining, error];
}

class InviteLinkCubit extends Cubit<InviteLinkState> {
  final String token;
  final GetInviteLinkPreviewUseCase getPreview;
  final AcceptInviteLinkUseCase acceptInviteLink;

  InviteLinkCubit({
    required this.token,
    required this.getPreview,
    required this.acceptInviteLink,
  }) : super(const InviteLinkState());

  Future<void> load() async {
    emit(const InviteLinkState());
    final result = await getPreview(token);
    result.fold(
      (failure) => emit(InviteLinkState(
        status: failure is NotFoundFailure
            ? InviteLinkStatus.unavailable
            : InviteLinkStatus.failure,
        error: failure is NotFoundFailure
            ? inviteLinkUnavailableMessage
            : failure.message,
      )),
      (preview) => emit(InviteLinkState(
        status: InviteLinkStatus.preview,
        preview: preview,
      )),
    );
  }

  Future<void> join() async {
    if (state.joining || state.preview == null) return;
    emit(InviteLinkState(
      status: InviteLinkStatus.preview,
      preview: state.preview,
      joining: true,
    ));
    final result = await acceptInviteLink(token);
    result.fold(
      (failure) => emit(InviteLinkState(
        status: failure is NotFoundFailure
            ? InviteLinkStatus.unavailable
            : InviteLinkStatus.preview,
        preview: failure is NotFoundFailure ? null : state.preview,
        error: failure is NotFoundFailure
            ? inviteLinkUnavailableMessage
            : failure.message,
      )),
      (invitation) => emit(InviteLinkState(
        status: InviteLinkStatus.joined,
        preview: state.preview,
        invitation: invitation,
      )),
    );
  }
}
