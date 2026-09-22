import 'package:flutter/material.dart';

import '../../domain/entities/streaming.dart';

class ReactionsOverlayWidget extends StatefulWidget {
  final List<Reaction> reactions;
  
  const ReactionsOverlayWidget({super.key, required this.reactions});
  
  @override
  State<ReactionsOverlayWidget> createState() => _ReactionsOverlayWidgetState();
}

class _ReactionsOverlayWidgetState extends State<ReactionsOverlayWidget>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animation;
  
  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(seconds: 3),
      vsync: this,
    );
    
    _animation = Tween<double>(begin: 1.0, end: 0.0).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeOut),
    );
    
    _controller.forward();
  }
  
  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
  
  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _animation,
      builder: (context, child) {
        return Stack(
          children: widget.reactions.map((reaction) {
            final offset = Offset(
              reaction.x * MediaQuery.of(context).size.width,
              reaction.y * MediaQuery.of(context).size.height * (1 - _animation.value),
            );
            
            return Positioned(
              left: offset.dx,
              top: offset.dy,
              child: Opacity(
                opacity: _animation.value,
                child: Transform.scale(
                  scale: 1 + (1 - _animation.value) * 0.5,
                  child: Text(
                    reaction.emoji,
                    style: const TextStyle(fontSize: 32),
                  ),
                ),
              ),
            );
          }).toList(),
        );
      },
    );
  }
}
