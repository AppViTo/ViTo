import 'package:flutter/material.dart';

import '../../domain/entities/archive.dart';

class ArchiveStatsWidget extends StatelessWidget {
  final EventStatistics statistics;
  
  const ArchiveStatsWidget({super.key, required this.statistics});
  
  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Event Statistics',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _buildStatItem(
                  Icons.people,
                  'Attendees',
                  statistics.totalAttendees.toString(),
                  Colors.blue,
                ),
                _buildStatItem(
                  Icons.check_circle,
                  'Check-ins',
                  statistics.totalCheckIns.toString(),
                  Colors.green,
                ),
                _buildStatItem(
                  Icons.photo,
                  'Photos',
                  statistics.totalPhotos.toString(),
                  Colors.orange,
                ),
                _buildStatItem(
                  Icons.favorite,
                  'Reactions',
                  statistics.totalReactions.toString(),
                  Colors.red,
                ),
              ],
            ),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _buildStatItem(
                  Icons.comment,
                  'Comments',
                  statistics.totalComments.toString(),
                  Colors.purple,
                ),
                _buildStatItem(
                  Icons.share,
                  'Shares',
                  statistics.totalShares.toString(),
                  Colors.teal,
                ),
                _buildStatItem(
                  Icons.visibility,
                  'Peak Viewers',
                  statistics.peakConcurrentViewers.toString(),
                  Colors.indigo,
                ),
                _buildStatItem(
                  Icons.timer,
                  'Stream (min)',
                  statistics.totalStreamMinutes.toString(),
                  Colors.brown,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
  
  Widget _buildStatItem(
    IconData icon,
    String label,
    String value,
    Color color,
  ) {
    return Column(
      children: [
        Icon(icon, color: color, size: 32),
        const SizedBox(height: 8),
        Text(
          value,
          style: TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.bold,
            color: color,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          label,
          style: TextStyle(
            color: Colors.grey[600],
            fontSize: 12,
          ),
        ),
      ],
    );
  }
}
