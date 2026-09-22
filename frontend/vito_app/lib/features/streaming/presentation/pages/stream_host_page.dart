import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../bloc/streaming_bloc.dart';
import '../bloc/streaming_event.dart';
import '../bloc/streaming_state.dart';
import '../widgets/stream_stats_widget.dart';
import '../../../../core/di/injection_container.dart';

class StreamHostPage extends StatefulWidget {
  final String eventId;
  final String eventTitle;
  
  const StreamHostPage({
    super.key,
    required this.eventId,
    required this.eventTitle,
  });
  
  @override
  State<StreamHostPage> createState() => _StreamHostPageState();
}

class _StreamHostPageState extends State<StreamHostPage> {
  late StreamingBloc _streamingBloc;
  String? _streamKey;
  String? _playbackUrl;
  bool _isStreaming = false;
  
  @override
  void initState() {
    super.initState();
    _streamingBloc = sl<StreamingBloc>();
  }
  
  @override
  void dispose() {
    _streamingBloc.close();
    super.dispose();
  }
  
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Go Live'),
        backgroundColor: Colors.deepPurple,
      ),
      body: BlocListener<StreamingBloc, StreamingState>(
        bloc: _streamingBloc,
        listener: (context, state) {
          if (state is StreamCreated) {
            setState(() {
              _streamKey = state.stream.streamKey;
              _playbackUrl = state.stream.playbackUrl;
            });
            _showStreamKeyDialog();
          }
          
          if (state is StreamActive) {
            setState(() {
              _isStreaming = true;
            });
          }
          
          if (state is StreamEnded) {
            setState(() {
              _isStreaming = false;
            });
            Navigator.pop(context);
          }
          
          if (state is StreamingError) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(state.message),
                backgroundColor: Colors.red,
              ),
            );
          }
        },
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.eventTitle,
                        style: const TextStyle(
                          fontSize: 24,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Event ID: ${widget.eventId}',
                        style: TextStyle(
                          color: Colors.grey[600],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              
              if (_isStreaming) ...[
                StreamStatsWidget(
                  streamId: _streamKey ?? '',
                ),
                const SizedBox(height: 16),
              ],
              
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Stream Settings',
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 16),
                      _buildSettingRow(
                        'Enable Chat',
                        true,
                        (value) {},
                      ),
                      _buildSettingRow(
                        'Enable Reactions',
                        true,
                        (value) {},
                      ),
                      _buildSettingRow(
                        'Enable Recording',
                        true,
                        (value) {},
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 24),
              
              if (!_isStreaming)
                ElevatedButton(
                  onPressed: () {
                    _streamingBloc.add(CreateStream(
                      eventId: widget.eventId,
                      title: widget.eventTitle,
                    ));
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.deepPurple,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: const Text(
                    'Create Stream',
                    style: TextStyle(
                      fontSize: 18,
                      color: Colors.white,
                    ),
                  ),
                ),
              
              if (_isStreaming)
                ElevatedButton(
                  onPressed: () {
                    _streamingBloc.add(StopStream(streamId: _streamKey!));
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.red,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: const Text(
                    'End Stream',
                    style: TextStyle(
                      fontSize: 18,
                      color: Colors.white,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
  
  Widget _buildSettingRow(String title, bool value, ValueChanged<bool> onChanged) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            title,
            style: const TextStyle(fontSize: 16),
          ),
          Switch(
            value: value,
            onChanged: onChanged,
            activeColor: Colors.deepPurple,
          ),
        ],
      ),
    );
  }
  
  void _showStreamKeyDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Stream Key'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Use this key in your streaming software:'),
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.grey[200],
                borderRadius: BorderRadius.circular(8),
              ),
              child: SelectableText(
                _streamKey ?? '',
                style: const TextStyle(
                  fontFamily: 'monospace',
                  fontSize: 16,
                ),
              ),
            ),
            const SizedBox(height: 16),
            const Text('Playback URL:'),
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.grey[200],
                borderRadius: BorderRadius.circular(8),
              ),
              child: SelectableText(
                _playbackUrl ?? '',
                style: const TextStyle(
                  fontFamily: 'monospace',
                  fontSize: 14,
                ),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.pop(context);
              _streamingBloc.add(StartStream(streamId: _streamKey!));
            },
            child: const Text('Start Streaming'),
          ),
        ],
      ),
    );
  }
}
