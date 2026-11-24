import 'package:dartz/dartz.dart';
import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../../core/error/failures.dart';
import '../../domain/entities/attendee.dart';
import '../../domain/entities/invitation.dart';
import '../../domain/usecases/list_event_attendees.dart';
import '../../domain/usecases/list_event_invitations.dart';

/// Una lista paginada del organizador: primera página en curso, página
/// siguiente en curso, el final (ya llegó una página vacía) y el error de la
/// última página pedida.
class GuestList<T> extends Equatable {
  /// Primera página en curso.
  final bool loading;

  /// Página siguiente en curso.
  final bool loadingMore;

  /// Ya llegó una página vacía: no hay más.
  final bool reachedEnd;

  final List<T> items;
  final String? error;

  const GuestList({
    this.loading = true,
    this.loadingMore = false,
    this.reachedEnd = false,
    this.items = const [],
    this.error,
  });

  /// Copia; [error] se borra si no se pasa.
  GuestList<T> copyWith({
    bool? loading,
    bool? loadingMore,
    bool? reachedEnd,
    List<T>? items,
    String? error,
  }) =>
      GuestList(
        loading: loading ?? this.loading,
        loadingMore: loadingMore ?? this.loadingMore,
        reachedEnd: reachedEnd ?? this.reachedEnd,
        items: items ?? this.items,
        error: error,
      );

  /// Se puede pedir la página siguiente: la primera ya llegó, no hay otra en
  /// curso, no se llegó al final y la lista no está vacía por un error.
  bool get canLoadMore =>
      !loading && !loadingMore && !reachedEnd && !(error != null && items.isEmpty);

  @override
  List<Object?> get props => [loading, loadingMore, reachedEnd, items, error];
}

/// Asistentes (T058) e invitaciones (T052) de un evento, solo para su
/// organizador. Se cargan a la vez, cada una con su paginación, y cada lista
/// falla por separado.
class EventGuestsState extends Equatable {
  final GuestList<Attendee> attendees;
  final GuestList<Invitation> invitations;

  const EventGuestsState({
    this.attendees = const GuestList(),
    this.invitations = const GuestList(),
  });

  /// Nombre visible de un invitado: `Invitation` solo trae su id, y
  /// `Attendee` trae el nombre de quien ya respondió.
  String? displayNameOf(String userId) {
    for (final attendee in attendees.items) {
      if (attendee.userId == userId) return attendee.displayName;
    }
    return null;
  }

  EventGuestsState copyWith({
    GuestList<Attendee>? attendees,
    GuestList<Invitation>? invitations,
  }) =>
      EventGuestsState(
        attendees: attendees ?? this.attendees,
        invitations: invitations ?? this.invitations,
      );

  @override
  List<Object?> get props => [attendees, invitations];
}

/// Paginación infinita de cada lista, como `FollowListCubit` (T-F13): pide
/// páginas hasta recibir una vacía, sin repetir elementos. Una página puede
/// traer menos de `size`, así que una página corta no es el final.
class EventGuestsCubit extends Cubit<EventGuestsState> {
  final String eventId;
  final ListEventAttendeesUseCase listAttendees;
  final ListEventInvitationsUseCase listInvitations;

  int _nextAttendeesPage = 0;
  int _nextInvitationsPage = 0;

  /// Sube con cada [load]: las respuestas de una carga anterior se ignoran.
  int _generation = 0;

  EventGuestsCubit({
    required this.eventId,
    required this.listAttendees,
    required this.listInvitations,
  }) : super(const EventGuestsState());

  /// Carga (o recarga) la primera página de las dos listas.
  Future<void> load() async {
    final generation = ++_generation;
    _nextAttendeesPage = 0;
    _nextInvitationsPage = 0;
    emit(const EventGuestsState());
    await Future.wait([
      _fetchAttendees(generation),
      _fetchInvitations(generation),
    ]);
  }

  /// Página siguiente de asistentes, si la hay y no hay otra en curso.
  Future<void> loadMoreAttendees() async {
    if (isClosed || !state.attendees.canLoadMore) return;
    emit(state.copyWith(
      attendees: state.attendees.copyWith(loadingMore: true),
    ));
    await _fetchAttendees(_generation);
  }

  /// Página siguiente de invitaciones, si la hay y no hay otra en curso.
  Future<void> loadMoreInvitations() async {
    if (isClosed || !state.invitations.canLoadMore) return;
    emit(state.copyWith(
      invitations: state.invitations.copyWith(loadingMore: true),
    ));
    await _fetchInvitations(_generation);
  }

  Future<void> _fetchAttendees(int generation) async {
    final page = _nextAttendeesPage;
    final result = await listAttendees(
      EventGuestsPageParams(eventId: eventId, page: page),
    );
    // La página puede cerrarse (o recargarse) con la petición en curso.
    if (isClosed || generation != _generation) return;
    if (result.isRight()) _nextAttendeesPage = page + 1;
    emit(state.copyWith(
      attendees: _merge(state.attendees, result, (a) => a.userId),
    ));
  }

  Future<void> _fetchInvitations(int generation) async {
    final page = _nextInvitationsPage;
    final result = await listInvitations(
      EventGuestsPageParams(eventId: eventId, page: page),
    );
    if (isClosed || generation != _generation) return;
    if (result.isRight()) _nextInvitationsPage = page + 1;
    emit(state.copyWith(
      invitations: _merge(state.invitations, result, (i) => i.id),
    ));
  }

  /// Agrega una página a [list] sin repetir elementos (por [keyOf]); una
  /// página vacía marca el final.
  static GuestList<T> _merge<T>(
    GuestList<T> list,
    Either<Failure, List<T>> result,
    Object Function(T item) keyOf,
  ) =>
      result.fold(
        (failure) => list.copyWith(
          loading: false,
          loadingMore: false,
          error: failure.message,
        ),
        (page) {
          final seen = list.items.map(keyOf).toSet();
          return list.copyWith(
            loading: false,
            loadingMore: false,
            reachedEnd: page.isEmpty,
            items: [...list.items, ...page.where((item) => seen.add(keyOf(item)))],
          );
        },
      );
}
