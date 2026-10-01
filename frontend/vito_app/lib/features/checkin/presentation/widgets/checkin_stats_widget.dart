import 'package:flutter/material.dart';
import '../../domain/entities/checkin_event.dart';

class CheckInStatsWidget extends StatelessWidget {
  final CheckInStats stats;
  
  const CheckInStatsWidget({super.key, required this.stats});
  
  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.all(16),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Check-In Statistics',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 16),
            
            // Progress bar
            LinearProgressIndicator(
              value: stats.checkInRate / 100,
              backgroundColor: Colors.grey[200],
              valueColor: AlwaysStoppedAnimation<Color>(
                stats.checkInRate >= 80 ? Colors.green :
                stats.checkInRate >= 50 ? Colors.orange : Colors.red,
              ),
              minHeight: 10,
            ),
            const SizedBox(height: 8),
            
            Center(
              child: Text(
                '${stats.checkInRate.toStringAsFixed(1)}% Checked In',
                style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
            
            const SizedBox(height: 24),
            
            // Stats grid
            GridView.count(
              crossAxisCount: 2,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              mainAxisSpacing: 16,
              crossAxisSpacing: 16,
              childAspectRatio: 1.5,
              children: [
                _buildStatCard(
                  context,
                  'Total Expected',
                  stats.totalExpected.toString(),
                  Icons.people,
                  Colors.blue,
                ),
                _buildStatCard(
                  context,
                  'Checked In',
                  stats.totalCheckedIn.toString(),
                  Icons.check_circle,
                  Colors.green,
                ),
                _buildStatCard(
                  context,
                  'Pending',
                  stats.totalPending.toString(),
                  Icons.pending,
                  Colors.orange,
                ),
                _buildStatCard(
                  context,
                  'Expired',
                  stats.totalExpired.toString(),
                  Icons.access_time,
                  Colors.red,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
  
  Widget _buildStatCard(
    BuildContext context,
    String label,
    String value,
    IconData icon,
    Color color,
  ) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, color: color, size: 24),
          const SizedBox(height: 8),
          Text(
            value,
            style: TextStyle(
              fontSize: 24,
              fontWeight: FontWeight.bold,
              color: color,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              color: color.withValues(alpha: 0.8),
            ),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}
