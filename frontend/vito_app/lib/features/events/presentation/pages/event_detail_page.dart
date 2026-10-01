import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:intl/intl.dart';
import '../../../../core/theme/app_colors.dart';
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
  @override
  void initState() {
    super.initState();
    context.read<EventBloc>().add(LoadEvent(widget.eventId));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: BlocBuilder<EventBloc, EventState>(
        builder: (context, state) {
          if (state is EventLoading) {
            return const Center(
              child: CircularProgressIndicator(color: AppColors.primary),
            );
          }

          if (state is EventError) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.error_outline, size: 64, color: AppColors.error),
                  const SizedBox(height: 16),
                  Text(state.message),
                  const SizedBox(height: 16),
                  ElevatedButton(
                    onPressed: () {
                      context.read<EventBloc>().add(LoadEvent(widget.eventId));
                    },
                    child: const Text('Retry'),
                  ),
                ],
              ),
            );
          }

          if (state is EventLoaded) {
            final event = state.event;
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
                              event.settings?.isPublic == true ? 'Public' : 'Private',
                              style: const TextStyle(color: AppColors.textSecondary),
                            ),
                          ],
                        ),
                        const SizedBox(height: 16),

                        // Description
                        if (event.description != null) ...[
                          const Text(
                            'About',
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
                            'Date & Time',
                            DateFormat('EEEE, MMMM d, yyyy • h:mm a')
                                .format(event.details!.startDate!),
                          ),
                          const SizedBox(height: 12),
                        ],

                        // Location
                        if (event.details?.location != null) ...[
                          _buildInfoRow(
                            Icons.location_on,
                            'Location',
                            event.details!.location!,
                          ),
                          const SizedBox(height: 12),
                        ],

                        // Address
                        if (event.details?.address != null) ...[
                          _buildInfoRow(
                            Icons.map,
                            'Address',
                            event.details!.address!,
                          ),
                          const SizedBox(height: 12),
                        ],

                        // Category
                        if (event.details?.category != null) ...[
                          _buildInfoRow(
                            Icons.category,
                            'Category',
                            event.details!.category!,
                          ),
                          const SizedBox(height: 12),
                        ],

                        // Stats
                        if (event.stats != null) ...[
                          const SizedBox(height: 8),
                          const Text(
                            'Statistics',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(height: 12),
                          _buildStatsGrid(event.stats!),
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
        _buildStatCard(Icons.people, '${stats.totalInvited}', 'Invited'),
        _buildStatCard(Icons.check_circle, '${stats.totalAccepted}', 'Accepted'),
        _buildStatCard(Icons.cancel, '${stats.totalDeclined}', 'Declined'),
        _buildStatCard(Icons.help_outline, '${stats.totalMaybe}', 'Maybe'),
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

  Widget _buildActionButtons(BuildContext context, event) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // RSVP Button
        ElevatedButton.icon(
          onPressed: () {
            _showRSVPDialog(context, event.id);
          },
          icon: const Icon(Icons.how_to_reg),
          label: const Text('RSVP'),
        ),
        const SizedBox(height: 8),

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
          label: const Text('Check In'),
        ),
        const SizedBox(height: 8),

        // Mosaic Button
        OutlinedButton.icon(
          onPressed: () {
            Navigator.pushNamed(
              context,
              '/mosaic',
              arguments: {'eventId': event.id},
            );
          },
          icon: const Icon(Icons.grid_view),
          label: const Text('Mosaic'),
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
          label: const Text('Start Stream'),
        ),
      ],
    );
  }

  void _showRSVPDialog(BuildContext context, String eventId) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('RSVP'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.check_circle, color: AppColors.success),
              title: const Text('Accept'),
              onTap: () {
                context.read<EventBloc>().add(RespondToRSVP(
                  eventId: eventId,
                  status: RSVPStatus.accepted,
                ));
                Navigator.pop(context);
              },
            ),
            ListTile(
              leading: const Icon(Icons.help_outline, color: AppColors.warning),
              title: const Text('Maybe'),
              onTap: () {
                context.read<EventBloc>().add(RespondToRSVP(
                  eventId: eventId,
                  status: RSVPStatus.maybe,
                ));
                Navigator.pop(context);
              },
            ),
            ListTile(
              leading: const Icon(Icons.cancel, color: AppColors.error),
              title: const Text('Decline'),
              onTap: () {
                context.read<EventBloc>().add(RespondToRSVP(
                  eventId: eventId,
                  status: RSVPStatus.declined,
                ));
                Navigator.pop(context);
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatusChip(EventStatus status) {
    Color color;
    String text;

    switch (status) {
      case EventStatus.draft:
        color = AppColors.grey500;
        text = 'Draft';
        break;
      case EventStatus.published:
        color = AppColors.success;
        text = 'Published';
        break;
      case EventStatus.cancelled:
        color = AppColors.error;
        text = 'Cancelled';
        break;
      case EventStatus.completed:
        color = AppColors.info;
        text = 'Completed';
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
