import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../bloc/streaming_bloc.dart';
import '../bloc/streaming_event.dart';
import '../bloc/streaming_state.dart';
import '../../domain/entities/streaming.dart';
import '../widgets/stream_player_widget.dart';
import '../widgets/reactions_overlay_widget.dart';
import '../widgets/chat_widget.dart';
import '../../../../core/di/injection_container.dart';

class StreamViewerPage extends StatefulWidget {
  final String streamId;
  
  const StreamViewerPage({super.key, required this.streamId});
  
  @override
  State<StreamViewerPage> createState() => _StreamViewerPageState();
}

class _StreamViewerPageState extends State<StreamViewerPage> {
  late StreamingBloc _streamingBloc;
  bool _showChat = true;
  
  @override
  void initState() {
    super.initState();
    _streamingBloc = sl<StreamingBloc>();
    _streamingBloc.add(LoadStream(streamId: widget.streamId));
    _streamingBloc.add(ConnectToStream(
      streamId: widget.streamId,
      baseUrl: 'http://localhost:8086',
    ));
  }
  
  @override
  void dispose() {
    _streamingBloc.add(DisconnectFromStream());
    _streamingBloc.close();
    super.dispose();
  }
  
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        elevation: 0,
        actions: [
          IconButton(
            icon: Icon(
              _showChat ? Icons.chat : Icons.chat_outlined,
              color: Colors.white,
            ),
            onPressed: () {
              setState(() {
                _showChat = !_showChat;
              });
            },
          ),
        ],
      ),
      body: BlocBuilder<StreamingBloc, StreamingState>(
        bloc: _streamingBloc,
        builder: (context, state) {
          if (state is StreamingLoading) {
            return const Center(
              child: CircularProgressIndicator(color: Colors.white),
            );
          }
          
          if (state is StreamActive) {
            return Row(
              children: [
                Expanded(
                  flex: _showChat ? 3 : 5,
                  child: Stack(
                    children: [
                      StreamPlayerWidget(
                        streamUrl: state.stream.playbackUrl,
                      ),
                      ReactionsOverlayWidget(
                        reactions: state.reactions,
                      ),
                      Positioned(
                        top: 16,
                        left: 16,
                        child: _buildStreamInfo(state),
                      ),
                      Positioned(
                        bottom: 16,
                        right: 16,
                        child: _buildReactionButtons(state),
                      ),
                    ],
                  ),
                ),
                if (_showChat)
                  Expanded(
                    flex: 2,
                    child: ChatWidget(
                      streamId: widget.streamId,
                      messages: state.messages,
                      onSendMessage: (message) {
                        _streamingBloc.add(SendChatMessage(
                          streamId: widget.streamId,
                          message: message,
                        ));
                      },
                    ),
                  ),
              ],
            );
          }
          
          if (state is StreamingError) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.error, color: Colors.red, size: 64),
                  const SizedBox(height: 16),
                  Text(
                    state.message,
                    style: const TextStyle(color: Colors.white),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 16),
                  ElevatedButton(
                    onPressed: () {
                      _streamingBloc.add(LoadStream(streamId: widget.streamId));
                    },
                    child: const Text('Retry'),
                  ),
                ],
              ),
            );
          }
          
          return const SizedBox.shrink();
        },
      ),
    );
  }
  
  Widget _buildStreamInfo(StreamActive state) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.black.withOpacity(0.6),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 8,
                height: 8,
                decoration: const BoxDecoration(
                  color: Colors.red,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 8),
              const Text(
                'LIVE',
                style: TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                '${state.viewerCount} viewers',
                style: const TextStyle(color: Colors.white),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            state.stream.title,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 16,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }
  
  Widget _buildReactionButtons(StreamActive state) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        _buildReactionButton(
          icon: '❤️',
          onTap: () {
            _streamingBloc.add(SendReaction(
              streamId: widget.streamId,
              type: ReactionType.heart,
              emoji: '❤️',
            ));
          },
        ),
        const SizedBox(height: 8),
        _buildReactionButton(
          icon: '🔥',
          onTap: () {
            _streamingBloc.add(SendReaction(
              streamId: widget.streamId,
              type: ReactionType.fire,
              emoji: '🔥',
            ));
          },
        ),
        const SizedBox(height: 8),
        _buildReactionButton(
          icon: '👏',
          onTap: () {
            _streamingBloc.add(SendReaction(
              streamId: widget.streamId,
              type: ReactionType.clap,
              emoji: '👏',
            ));
          },
        ),
        const SizedBox(height: 8),
        _buildReactionButton(
          icon: '🎉',
          onTap: () {
            _streamingBloc.add(SendReaction(
              streamId: widget.streamId,
              type: ReactionType.celebration,
              emoji: '🎉',
            ));
          },
        ),
      ],
    );
  }
  
  Widget _buildReactionButton({
    required String icon,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 48,
        height: 48,
        decoration: BoxDecoration(
          color: Colors.white.withOpacity(0.2),
          shape: BoxShape.circle,
        ),
        child: Center(
          child: Text(
            icon,
            style: const TextStyle(fontSize: 24),
          ),
        ),
      ),
    );
  }
}
