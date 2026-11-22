import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:intl/intl.dart';

import '../../../../core/theme/app_colors.dart';
import '../../domain/entities/attendee.dart';
import '../../domain/entities/invitation.dart';
import '../../domain/entities/rsvp.dart';
import '../bloc/event_guests_cubit.dart';

/// Invitados del evento, solo para su organizador: quién respondió
/// (`listEventAttendees`, T058) y a quién se invitó, desde la app o por
/// enlace (`listEventInvitations`, T052). Nunca muestra correo ni teléfono.
class EventGuestsPage extends StatefulWidget {
  final String eventTitle;

  const EventGuestsPage({super.key, required this.eventTitle});

  @override
  State<EventGuestsPage> createState() => _EventGuestsPageState();
}

class _EventGuestsPageState extends State<EventGuestsPage> {
  @override
  void initState() {
    super.initState();
    context.read<EventGuestsCubit>().load();
  }

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: Text(widget.eventTitle.isEmpty ? 'Invitados' : widget.eventTitle),
          bottom: const TabBar(
            tabs: [Tab(text: 'Asistentes'), Tab(text: 'Invitaciones')],
          ),
        ),
        body: BlocBuilder<EventGuestsCubit, EventGuestsState>(
          builder: (context, state) => TabBarView(
            children: [
              _ListSection<Attendee>(
                list: state.attendees,
                emptyMessage: 'Aún nadie ha respondido',
                itemBuilder: _attendeeTile,
              ),
              _ListSection<Invitation>(
                list: state.invitations,
                emptyMessage: 'Aún no has invitado a nadie',
                itemBuilder: (invitation) => _invitationTile(state, invitation),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _attendeeTile(Attendee attendee) {
    final responded = attendee.respondedAt;
    return ListTile(
      leading: Icon(_rsvpIcon(attendee.status), color: _rsvpColor(attendee.status)),
      title: Text(attendee.displayName ?? 'Invitado sin nombre'),
      subtitle: Text([
        _rsvpLabel(attendee.status),
        if (responded != null) 'respondió el ${_date(responded)}',
      ].join(' · ')),
    );
  }

  Widget _invitationTile(EventGuestsState state, Invitation invitation) {
    final name = state.displayNameOf(invitation.inviteeId);
    final sent = invitation.sentAt;
    final responded = invitation.respondedAt;
    return ListTile(
      leading: Icon(
        invitation.viaInviteLink ? Icons.link : Icons.person_add_alt,
        color: AppColors.textSecondary,
      ),
      title: Text(name ?? 'Invitado'),
      subtitle: Text([
        _invitationLabel(invitation.status),
        invitation.viaInviteLink ? 'por enlace' : 'desde la app',
        if (sent != null) 'enviada el ${_date(sent)}',
        if (responded != null) 'respondió el ${_date(responded)}',
      ].join(' · ')),
    );
  }

  static String _date(DateTime date) =>
      DateFormat("d 'de' MMMM", 'es').format(date);

  static String _rsvpLabel(RSVPStatus status) {
    switch (status) {
      case RSVPStatus.accepted:
        return 'Va';
      case RSVPStatus.waitlisted:
        return 'En lista de espera';
      case RSVPStatus.maybe:
        return 'Tal vez';
      case RSVPStatus.declined:
        return 'No va';
      case RSVPStatus.pending:
        return 'Sin responder';
    }
  }

  static IconData _rsvpIcon(RSVPStatus status) {
    switch (status) {
      case RSVPStatus.accepted:
        return Icons.check_circle;
      case RSVPStatus.waitlisted:
        return Icons.hourglass_top;
      case RSVPStatus.maybe:
        return Icons.help_outline;
      case RSVPStatus.declined:
        return Icons.cancel;
      case RSVPStatus.pending:
        return Icons.schedule;
    }
  }

  static Color _rsvpColor(RSVPStatus status) {
    switch (status) {
      case RSVPStatus.accepted:
        return AppColors.success;
      case RSVPStatus.waitlisted:
      case RSVPStatus.maybe:
        return AppColors.warning;
      case RSVPStatus.declined:
        return AppColors.error;
      case RSVPStatus.pending:
        return AppColors.textSecondary;
    }
  }

  static String _invitationLabel(InvitationStatus status) {
    switch (status) {
      case InvitationStatus.pending:
        return 'Pendiente';
      case InvitationStatus.accepted:
        return 'Aceptada';
      case InvitationStatus.declined:
        return 'Rechazada';
      case InvitationStatus.expired:
        return 'Vencida';
    }
  }
}

class _ListSection<T> extends StatelessWidget {
  final GuestList<T> list;
  final String emptyMessage;
  final Widget Function(T item) itemBuilder;

  const _ListSection({
    required this.list,
    required this.emptyMessage,
    required this.itemBuilder,
  });

  @override
  Widget build(BuildContext context) {
    if (list.loading) {
      return const Center(child: CircularProgressIndicator());
    }
    final error = list.error;
    if (error != null || list.items.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(error ?? emptyMessage, textAlign: TextAlign.center),
              if (error != null) ...[
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: () => context.read<EventGuestsCubit>().load(),
                  child: const Text('Reintentar'),
                ),
              ],
            ],
          ),
        ),
      );
    }
    return ListView(children: [for (final item in list.items) itemBuilder(item)]);
  }
}
