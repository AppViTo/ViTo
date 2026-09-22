import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../bloc/streaming_bloc.dart';
import '../bloc/streaming_state.dart';

class StreamStatsWidget extends StatelessWidget {
  final String streamId;
  
  const StreamStatsWidget({super.key, required this.streamId});
  
  @override
  Widget build(BuildContext context) {
    return BlocBuilder<StreamingBloc, StreamingState>(
      builder: (context, state) {
        if (state is StreamActive) {
          return Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Stream Stats',
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
                        Icons.visibility,
                        'Viewers',
                        state.viewerCount.toString(),
                        Colors.blue,
                      ),
                      _buildStatItem(
                        Icons.favorite,
                        'Reactions',
                        state.reactions.length.toString(),
                        Colors.red,
                      ),
                      _buildStatItem(
                        Icons.chat,
                        'Messages',
                        state.messages.length.toString(),
                        Colors.green,
                      ),
                    ],
                  ),
                ],
              ),
            ),
          );
        }
        
        return const SizedBox.shrink();
      },
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
            fontSize: 24,
            fontWeight: FontWeight.bold,
            color: color,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          label,
          style: TextStyle(
            color: Colors.grey[600],
            fontSize: 14,
          ),
        ),
      ],
    );
  }
}
