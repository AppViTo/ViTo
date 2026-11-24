import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../domain/entities/invite_link.dart';
import '../../domain/usecases/create_invite_link.dart';
import '../../domain/usecases/list_invite_links.dart';
import '../../domain/usecases/revoke_invite_link.dart';

enum InviteLinksStatus { loading, ready, failure }

/// Enlaces de invitación de un evento, vistos por su organizador (T042).
class InviteLinksState extends Equatable {
  final InviteLinksStatus status;
  final List<InviteLink> links;

  /// Hay una creación o una revocación en curso.
  final bool busy;

  /// Mensaje de error de la última acción, para mostrarlo una vez.
  final String? error;

  /// Enlace recién creado, para ofrecer copiarlo o compartirlo.
  final InviteLink? created;

  const InviteLinksState({
    this.status = InviteLinksStatus.loading,
    this.links = const [],
    this.busy = false,
    this.error,
    this.created,
  });

  /// Enlaces que aún se pueden compartir, del más reciente al más antiguo.
  List<InviteLink> get activeLinks => links.where((l) => l.isActive).toList();

  InviteLinksState copyWith({
    InviteLinksStatus? status,
    List<InviteLink>? links,
    bool? busy,
    String? error,
    InviteLink? created,
  }) =>
      InviteLinksState(
        status: status ?? this.status,
        links: links ?? this.links,
        busy: busy ?? this.busy,
        error: error,
        created: created,
      );

  @override
  List<Object?> get props => [status, links, busy, error, created];
}

class InviteLinksCubit extends Cubit<InviteLinksState> {
  final String eventId;
  final ListInviteLinksUseCase listInviteLinks;
  final CreateInviteLinkUseCase createInviteLink;
  final RevokeInviteLinkUseCase revokeInviteLink;

  InviteLinksCubit({
    required this.eventId,
    required this.listInviteLinks,
    required this.createInviteLink,
    required this.revokeInviteLink,
  }) : super(const InviteLinksState());

  Future<void> load() async {
    emit(state.copyWith(status: InviteLinksStatus.loading));
    final result = await listInviteLinks(eventId);
    // La página puede cerrarse con la petición en curso.
    if (isClosed) return;
    result.fold(
      (failure) => emit(state.copyWith(
        status: InviteLinksStatus.failure,
        error: failure.message,
      )),
      (links) => emit(state.copyWith(
        status: InviteLinksStatus.ready,
        links: _sorted(links),
      )),
    );
  }

  Future<void> create({int? expiresInDays, int? maxUses}) async {
    if (state.busy) return;
    emit(state.copyWith(busy: true));
    final result = await createInviteLink(CreateInviteLinkParams(
      eventId: eventId,
      expiresInDays: expiresInDays,
      maxUses: maxUses,
    ));
    if (isClosed) return;
    result.fold(
      (failure) => emit(state.copyWith(busy: false, error: failure.message)),
      (link) => emit(state.copyWith(
        status: InviteLinksStatus.ready,
        busy: false,
        links: _sorted([link, ...state.links.where((l) => l.id != link.id)]),
        created: link,
      )),
    );
  }

  Future<void> revoke(String linkId) async {
    if (state.busy) return;
    emit(state.copyWith(busy: true));
    final result = await revokeInviteLink(
      RevokeInviteLinkParams(eventId: eventId, linkId: linkId),
    );
    if (isClosed) return;
    result.fold(
      (failure) => emit(state.copyWith(busy: false, error: failure.message)),
      (_) => emit(state.copyWith(
        busy: false,
        links: [
          for (final link in state.links)
            link.id == linkId ? _revoked(link) : link,
        ],
      )),
    );
  }

  static List<InviteLink> _sorted(List<InviteLink> links) =>
      [...links]..sort((a, b) => b.createdAt.compareTo(a.createdAt));

  static InviteLink _revoked(InviteLink link) => InviteLink(
        id: link.id,
        eventId: link.eventId,
        token: link.token,
        url: link.url,
        expiresAt: link.expiresAt,
        maxUses: link.maxUses,
        uses: link.uses,
        revoked: true,
        createdAt: link.createdAt,
      );
}
