import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

class StreamPlayerWidget extends StatefulWidget {
  final String? streamUrl;
  
  const StreamPlayerWidget({super.key, this.streamUrl});
  
  @override
  State<StreamPlayerWidget> createState() => _StreamPlayerWidgetState();
}

class _StreamPlayerWidgetState extends State<StreamPlayerWidget> {
  late VideoPlayerController _controller;
  bool _isInitialized = false;
  String? _error;
  
  @override
  void initState() {
    super.initState();
    _initializePlayer();
  }
  
  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
  
  Future<void> _initializePlayer() async {
    if (widget.streamUrl == null) {
      setState(() {
        _error = 'Stream URL is not available';
      });
      return;
    }
    
    try {
      _controller = VideoPlayerController.networkUrl(
        Uri.parse(widget.streamUrl!),
      );
      
      await _controller.initialize();
      
      _controller.setLooping(true);
      _controller.play();
      
      setState(() {
        _isInitialized = true;
      });
    } catch (e) {
      setState(() {
        _error = 'Failed to initialize stream: $e';
      });
    }
  }
  
  @override
  Widget build(BuildContext context) {
    if (_error != null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error, color: Colors.red, size: 64),
            const SizedBox(height: 16),
            Text(
              _error!,
              style: const TextStyle(color: Colors.white),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      );
    }
    
    if (!_isInitialized) {
      return const Center(
        child: CircularProgressIndicator(color: Colors.white),
      );
    }
    
    return AspectRatio(
      aspectRatio: _controller.value.aspectRatio,
      child: VideoPlayer(_controller),
    );
  }
}
