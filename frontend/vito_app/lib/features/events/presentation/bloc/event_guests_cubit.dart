import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../domain/entities/attendee.dart';
import '../../domain/entities/invitation.dart';
import '../../domain/usecases/list_event_attendees.dart';
import '../../domain/usecases/list_event_invitations.dart';

/// Una lista del organizador: cargando, cargada o con el error del backend.
class GuestList<T> extends Equatable {
  final bool loading;
  final List<T> items;
  final String? error;

  const GuestList({this.loading = true, this.items = const [], this.error});

  @override
  List<Object?> get props => [loading, items, error];
}

/// Asistentes (T058) e invitaciones (T052) de un evento, solo para su
/// organizador. Se cargan a la vez y cada lista falla por separado.
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

  @override
  List<Object?> get props => [attendees, invitations];
}

class EventGuestsCubit extends Cubit<EventGuestsState> {
  final String eventId;
  final ListEventAttendeesUseCase listAttendees;
  final ListEventInvitationsUseCase listInvitations;

  EventGuestsCubit({
    required this.eventId,
    required this.listAttendees,
    required this.listInvitations,
  }) : super(const EventGuestsState());

  Future<void> load() async {
    emit(const EventGuestsState());
    final attendees = listAttendees(eventId);
    final invitations = listInvitations(eventId);

    final attendeesResult = await attendees;
    final invitationsResult = await invitations;
    emit(EventGuestsState(
      attendees: attendeesResult.fold(
        (failure) => GuestList(loading: false, error: failure.message),
        (items) => GuestList(loading: false, items: items),
      ),
      invitations: invitationsResult.fold(
        (failure) => GuestList(loading: false, error: failure.message),
        (items) => GuestList(loading: false, items: items),
      ),
    ));
  }
}
