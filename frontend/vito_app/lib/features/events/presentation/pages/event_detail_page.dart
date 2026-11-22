import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:intl/intl.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../auth/presentation/bloc/auth_bloc.dart';
import '../../domain/entities/event.dart';
import '../../domain/entities/rsvp.dart';
import '../bloc/event_bloc.dart';

class EventDetailPage extends StatefulWidget {
  final String eventId;

  const EventDetailPage({super.key, required this.eventId});

  @override
  State<EventDetailPage> createState() => _EventDetailPageState();
}

class _EventDetailPageState extends State<EventDetailPage> {
  /// Último detalle cargado. Mientras exista, los estados de otras acciones
  /// del BLoC (responder, recargar) no reemplazan la página.
  Event? _event;

  /// Respuesta enviada en esta visita. El contrato no expone el RSVP propio
  /// al leer el evento, así que solo se conoce después de responder.
  RSVP? _myRsvp;
  bool _submittingRsvp = false;

  @override
  void initState() {
    super.initState();
    context.read<EventBloc>().add(LoadEvent(widget.eventId));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: BlocConsumer<EventBloc, EventState>(
        listenWhen: (_, state) =>
            state is RSVPSubmitted || (state is EventError && _event != null),
        listener: _onActionResult,
        buildWhen: (_, state) => _event == null || state is EventLoaded,
        builder: (context, state) {
          if (state is EventLoaded) _event = state.event;

          if (state is EventLoading) {
            return const Center(
              child: CircularProgressIndicator(color: AppColors.primary),
            );
          }

          // Sin AppBar en este estado: el botón de volver evita que el usuario
          // quede atrapado cuando el evento no existe (Bug-F01).
          if (state is EventError) {
            return SafeArea(
              child: Stack(
                children: [
                  Align(
                    alignment: Alignment.topLeft,
                    child: IconButton(
                      icon: const Icon(Icons.arrow_back),
                      tooltip: 'Volver',
                      onPressed: () => Navigator.of(context).maybePop(),
                    ),
                  ),
                  Center(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 24),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(Icons.error_outline, size: 64, color: AppColors.error),
                          const SizedBox(height: 16),
                          Text(state.message, textAlign: TextAlign.center),
                          const SizedBox(height: 16),
                          ElevatedButton(
                            onPressed: () {
                              context.read<EventBloc>().add(LoadEvent(widget.eventId));
                            },
                            child: const Text('Reintentar'),
                          ),
                          TextButton(
                            onPressed: () => Navigator.of(context).maybePop(),
                            child: const Text('Volver'),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            );
          }

          final event = _event;
          if (event != null) {
            return CustomScrollView(
              slivers: [
                // App bar with cover image
                SliverAppBar(
                  expandedHeight: 250,
                  pinned: true,
                  flexibleSpace: FlexibleSpaceBar(
                    title: Text(
                      event.title,
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        shadows: [Shadow(blurRadius: 8, color: Colors.black54)],
                      ),
                    ),
                    background: event.details?.coverImages != null &&
                            event.details!.coverImages!.isNotEmpty
                        ? Image.network(
                            event.details!.coverImages!.first,
                            fit: BoxFit.cover,
                            errorBuilder: (context, error, stackTrace) =>
                                _buildPlaceholder(),
                          )
                        : _buildPlaceholder(),
                  ),
                ),

                // Content
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // Status and visibility
                        Row(
                          children: [
                            _buildStatusChip(event.status),
                            const Spacer(),
                            Icon(
                              event.settings?.isPublic == true
                                  ? Icons.public
                                  : Icons.lock,
                              color: AppColors.grey500,
                            ),
                            const SizedBox(width: 4),
                            Text(
                              event.settings?.isPublic == true
                                  ? 'Público'
                                  : 'Privado',
                              style: const TextStyle(color: AppColors.textSecondary),
                            ),
                          ],
                        ),
                        const SizedBox(height: 16),

                        // Description
                        if (event.description != null) ...[
                          const Text(
                            'Descripción',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(height: 8),
                          Text(
                            event.description!,
                            style: const TextStyle(
                              fontSize: 15,
                              color: AppColors.textSecondary,
                              height: 1.5,
                            ),
                          ),
                          const SizedBox(height: 16),
                        ],

                        // Date and time
                        if (event.details?.startDate != null) ...[
                          _buildInfoRow(
                            Icons.calendar_today,
                            'Fecha y hora',
                            DateFormat(
                              "EEEE, d 'de' MMMM 'de' yyyy • h:mm a",
                              'es',
                            ).format(event.details!.startDate!),
                          ),
                          const SizedBox(height: 12),
                        ],

                        // Location
                        if (event.details?.location != null) ...[
                          _buildInfoRow(
                            Icons.location_on,
                            'Ubicación',
                            event.details!.location!,
                          ),
                          const SizedBox(height: 12),
                        ],

                        // Address
                        if (event.details?.address != null) ...[
                          _buildInfoRow(
                            Icons.map,
                            'Dirección',
                            event.details!.address!,
                          ),
                          const SizedBox(height: 12),
                        ],

                        // Category
                        if (event.details?.category != null) ...[
                          _buildInfoRow(
                            Icons.category,
                            'Categoría',
                            event.details!.category!,
                          ),
                          const SizedBox(height: 12),
                        ],

                        // Stats
                        if (event.stats != null) ...[
                          const SizedBox(height: 8),
                          const Text(
                            'Estadísticas',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(height: 12),
                          _buildStatsGrid(event.stats!),
                          const SizedBox(height: 16),
                        ],

                        // RSVP (T033): el organizador no responde a su evento.
                        if (!_isOrganizer(context, event)) ...[
                          _buildRsvpSection(context, event),
                          const SizedBox(height: 16),
                        ],

                        // Action buttons
                        const SizedBox(height: 8),
                        _buildActionButtons(context, event),
                      ],
                    ),
                  ),
                ),
              ],
            );
          }

          return const SizedBox.shrink();
        },
      ),
    );
  }

  Widget _buildPlaceholder() {
    return Container(
      color: AppColors.primary,
      child: const Center(
        child: Icon(Icons.event, size: 64, color: Colors.white),
      ),
    );
  }

  Widget _buildInfoRow(IconData icon, String label, String value) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 20, color: AppColors.primary),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: const TextStyle(
                  fontSize: 12,
                  color: AppColors.textSecondary,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                value,
                style: const TextStyle(fontSize: 15),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildStatsGrid(EventStats stats) {
    return GridView.count(
      crossAxisCount: 4,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      mainAxisSpacing: 8,
      crossAxisSpacing: 8,
      childAspectRatio: 1,
      children: [
        _buildStatCard(Icons.people, '${stats.totalInvited}', 'Invitados'),
        _buildStatCard(Icons.check_circle, '${stats.totalAccepted}', 'Confirmados'),
        _buildStatCard(Icons.cancel, '${stats.totalDeclined}', 'Rechazados'),
        _buildStatCard(Icons.help_outline, '${stats.totalMaybe}', 'Tal vez'),
      ],
    );
  }

  Widget _buildStatCard(IconData icon, String value, String label) {
    return Card(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 20, color: AppColors.primary),
          const SizedBox(height: 4),
          Text(
            value,
            style: const TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.bold,
            ),
          ),
          Text(
            label,
            style: const TextStyle(
              fontSize: 10,
              color: AppColors.textSecondary,
            ),
          ),
        ],
      ),
    );
  }

  void _onActionResult(BuildContext context, EventState state) {
    final messenger = ScaffoldMessenger.of(context);
    if (state is RSVPSubmitted) {
      setState(() {
        _myRsvp = state.rsvp;
        _submittingRsvp = false;
      });
      messenger.showSnackBar(
        SnackBar(content: Text(_rsvpConfirmation(state.rsvp.status))),
      );
      // Recarga el detalle para ver el contador de confirmados actualizado.
      context.read<EventBloc>().add(LoadEvent(widget.eventId));
    } else if (state is EventError) {
      setState(() => _submittingRsvp = false);
      messenger.showSnackBar(SnackBar(content: Text(state.message)));
    }
  }

  /// El detalle puede abrirse sin `AuthBloc` en las pruebas; sin sesión
  /// conocida no se trata al usuario como organizador.
  bool _isOrganizer(BuildContext context, Event event) {
    try {
      final authState = BlocProvider.of<AuthBloc>(context).state;
      return authState is AuthAuthenticated &&
          authState.user.id == event.organizerId;
    } catch (_) {
      return false;
    }
  }

  /// `respondRsvp` responde 409 en eventos cancelados, terminados o que ya
  /// empezaron; la app no ofrece responder en esos casos.
  bool _acceptsRsvp(Event event) {
    if (event.status == EventStatus.cancelled ||
        event.status == EventStatus.completed) {
      return false;
    }
    final start = event.details?.startDate;
    return start == null || start.isAfter(DateTime.now());
  }

  Widget _buildRsvpSection(BuildContext context, Event event) {
    final acceptsRsvp = _acceptsRsvp(event);
    final current = _myRsvp?.status;
    final enabled = acceptsRsvp && !_submittingRsvp;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text(
          '¿Vas a ir?',
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
        ),
        if (current != null) ...[
          const SizedBox(height: 4),
          Text(
            'Tu respuesta: ${_rsvpLabel(current)}',
            style: const TextStyle(color: AppColors.textSecondary),
          ),
        ],
        if (_myRsvp?.isWaitlisted == true) ...[
          const SizedBox(height: 8),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppColors.warning.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Row(
              children: [
                Icon(Icons.hourglass_top, color: AppColors.warning),
                SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Estás en lista de espera: el evento no tiene cupo por ahora.',
                  ),
                ),
              ],
            ),
          ),
        ],
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: _rsvpButton(
                context,
                event,
                status: RSVPStatus.accepted,
                label: 'Voy',
                icon: Icons.check_circle,
                selected: current == RSVPStatus.accepted ||
                    current == RSVPStatus.waitlisted,
                enabled: enabled,
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: _rsvpButton(
                context,
                event,
                status: RSVPStatus.maybe,
                label: 'Tal vez',
                icon: Icons.help_outline,
                selected: current == RSVPStatus.maybe,
                enabled: enabled,
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: _rsvpButton(
                context,
                event,
                status: RSVPStatus.declined,
                label: 'No voy',
                icon: Icons.cancel,
                selected: current == RSVPStatus.declined,
                enabled: enabled,
              ),
            ),
          ],
        ),
        if (!acceptsRsvp) ...[
          const SizedBox(height: 8),
          Text(
            event.status == EventStatus.cancelled
                ? 'El evento fue cancelado: ya no se puede responder.'
                : 'El evento ya empezó o terminó: ya no se puede responder.',
            style: const TextStyle(color: AppColors.textSecondary),
          ),
        ],
      ],
    );
  }

  Widget _rsvpButton(
    BuildContext context,
    Event event, {
    required RSVPStatus status,
    required String label,
    required IconData icon,
    required bool selected,
    required bool enabled,
  }) {
    final onPressed = enabled
        ? () {
            setState(() => _submittingRsvp = true);
            context
                .read<EventBloc>()
                .add(RespondToRSVP(eventId: event.id, status: status));
          }
        : null;
    final child = FittedBox(
      fit: BoxFit.scaleDown,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [Icon(icon, size: 18), const SizedBox(width: 4), Text(label)],
      ),
    );
    return selected
        ? ElevatedButton(onPressed: onPressed, child: child)
        : OutlinedButton(onPressed: onPressed, child: child);
  }

  String _rsvpLabel(RSVPStatus status) {
    switch (status) {
      case RSVPStatus.accepted:
        return 'Voy';
      case RSVPStatus.waitlisted:
        return 'Voy (en lista de espera)';
      case RSVPStatus.maybe:
        return 'Tal vez';
      case RSVPStatus.declined:
        return 'No voy';
      case RSVPStatus.pending:
        return 'Sin responder';
    }
  }

  String _rsvpConfirmation(RSVPStatus status) {
    switch (status) {
      case RSVPStatus.accepted:
        return '¡Listo! Confirmaste que vas';
      case RSVPStatus.waitlisted:
        return 'El evento está lleno: quedaste en lista de espera';
      case RSVPStatus.maybe:
        return 'Respondiste que tal vez vas';
      case RSVPStatus.declined:
        return 'Respondiste que no vas';
      case RSVPStatus.pending:
        return 'Respuesta registrada';
    }
  }

  Widget _buildActionButtons(BuildContext context, Event event) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // QR Scanner Button
        OutlinedButton.icon(
          onPressed: () {
            Navigator.pushNamed(
              context,
              '/qr-scanner',
              arguments: {'eventId': event.id},
            );
          },
          icon: const Icon(Icons.qr_code_scanner),
          label: const Text('Registrar entrada'),
        ),
        const SizedBox(height: 8),

        // Mosaic Button
        OutlinedButton.icon(
          onPressed: () {
            Navigator.pushNamed(
              context,
              '/mosaic',
              arguments: {
                'eventId': event.id,
                'organizerId': event.organizerId,
              },
            );
          },
          icon: const Icon(Icons.grid_view),
          label: const Text('Mosaico'),
        ),
        const SizedBox(height: 8),

        // Stream Button
        OutlinedButton.icon(
          onPressed: () {
            Navigator.pushNamed(
              context,
              '/stream-host',
              arguments: {
                'eventId': event.id,
                'eventTitle': event.title,
              },
            );
          },
          icon: const Icon(Icons.videocam),
          label: const Text('Iniciar transmisión'),
        ),
      ],
    );
  }

  Widget _buildStatusChip(EventStatus status) {
    Color color;
    String text;

    switch (status) {
      case EventStatus.draft:
        color = AppColors.grey500;
        text = 'Borrador';
        break;
      case EventStatus.published:
        color = AppColors.success;
        text = 'Publicado';
        break;
      case EventStatus.cancelled:
        color = AppColors.error;
        text = 'Cancelado';
        break;
      case EventStatus.completed:
        color = AppColors.info;
        text = 'Finalizado';
        break;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Text(
        text,
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w600,
          color: color,
        ),
      ),
    );
  }
}
