import 'package:flutter/material.dart';

class AppAnimations {
  // Duration constants
  static const Duration instant = Duration(milliseconds: 100);
  static const Duration fast = Duration(milliseconds: 200);
  static const Duration normal = Duration(milliseconds: 300);
  static const Duration slow = Duration(milliseconds: 500);
  static const Duration verySlow = Duration(milliseconds: 800);
  
  // Curves
  static const Curve easeIn = Curves.easeIn;
  static const Curve easeOut = Curves.easeOut;
  static const Curve easeInOut = Curves.easeInOut;
  static const Curve bounceIn = Curves.bounceIn;
  static const Curve bounceOut = Curves.bounceOut;
  static const Curve elasticIn = Curves.elasticIn;
  static const Curve elasticOut = Curves.elasticOut;
  
  // Fade transitions
  static Widget fadeIn({
    required Widget child,
    Duration duration = normal,
    Curve curve = easeIn,
    double begin = 0.0,
    double end = 1.0,
  }) {
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: begin, end: end),
      duration: duration,
      curve: curve,
      builder: (context, value, child) {
        return Opacity(
          opacity: value,
          child: child,
        );
      },
      child: child,
    );
  }
  
  static Widget fadeOut({
    required Widget child,
    Duration duration = normal,
    Curve curve = easeOut,
  }) {
    return fadeIn(
      child: child,
      duration: duration,
      curve: curve,
      begin: 1.0,
      end: 0.0,
    );
  }
  
  // Slide transitions
  static Widget slideIn({
    required Widget child,
    Duration duration = normal,
    Curve curve = easeOut,
    Offset begin = const Offset(0, 1),
    Offset end = Offset.zero,
  }) {
    return TweenAnimationBuilder<Offset>(
      tween: Tween<Offset>(begin: begin, end: end),
      duration: duration,
      curve: curve,
      builder: (context, value, child) {
        return Transform.translate(
          offset: value,
          child: child,
        );
      },
      child: child,
    );
  }
  
  static Widget slideInFromLeft({
    required Widget child,
    Duration duration = normal,
    Curve curve = easeOut,
  }) {
    return slideIn(
      child: child,
      duration: duration,
      curve: curve,
      begin: const Offset(-1, 0),
    );
  }
  
  static Widget slideInFromRight({
    required Widget child,
    Duration duration = normal,
    Curve curve = easeOut,
  }) {
    return slideIn(
      child: child,
      duration: duration,
      curve: curve,
      begin: const Offset(1, 0),
    );
  }
  
  static Widget slideInFromTop({
    required Widget child,
    Duration duration = normal,
    Curve curve = easeOut,
  }) {
    return slideIn(
      child: child,
      duration: duration,
      curve: curve,
      begin: const Offset(0, -1),
    );
  }
  
  static Widget slideInFromBottom({
    required Widget child,
    Duration duration = normal,
    Curve curve = easeOut,
  }) {
    return slideIn(
      child: child,
      duration: duration,
      curve: curve,
      begin: const Offset(0, 1),
    );
  }
  
  // Scale transitions
  static Widget scaleIn({
    required Widget child,
    Duration duration = normal,
    Curve curve = easeOut,
    double begin = 0.0,
    double end = 1.0,
    Alignment alignment = Alignment.center,
  }) {
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: begin, end: end),
      duration: duration,
      curve: curve,
      builder: (context, value, child) {
        return Transform.scale(
          scale: value,
          alignment: alignment,
          child: child,
        );
      },
      child: child,
    );
  }
  
  // Rotation transitions
  static Widget rotateIn({
    required Widget child,
    Duration duration = normal,
    Curve curve = easeOut,
    double begin = 0.0,
    double end = 1.0,
  }) {
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: begin, end: end),
      duration: duration,
      curve: curve,
      builder: (context, value, child) {
        return Transform.rotate(
          angle: value * 2 * 3.14159,
          child: child,
        );
      },
      child: child,
    );
  }
  
  // Size transitions
  static Widget sizeIn({
    required Widget child,
    required Size size,
    Duration duration = normal,
    Curve curve = easeOut,
  }) {
    return TweenAnimationBuilder<Size>(
      tween: Tween<Size>(
        begin: Size(size.width * 0.5, size.height * 0.5),
        end: size,
      ),
      duration: duration,
      curve: curve,
      builder: (context, value, child) {
        return SizedBox(
          width: value.width,
          height: value.height,
          child: child,
        );
      },
      child: child,
    );
  }
  
  // Combined animations
  static Widget slideAndFade({
    required Widget child,
    Duration duration = normal,
    Curve curve = easeOut,
    Offset slideBegin = const Offset(0, 1),
  }) {
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: 0, end: 1),
      duration: duration,
      curve: curve,
      builder: (context, value, child) {
        return Transform.translate(
          offset: Offset(
            slideBegin.dx * (1 - value),
            slideBegin.dy * (1 - value),
          ),
          child: Opacity(
            opacity: value,
            child: child,
          ),
        );
      },
      child: child,
    );
  }
  
  static Widget scaleAndFade({
    required Widget child,
    Duration duration = normal,
    Curve curve = easeOut,
    double scaleBegin = 0.8,
  }) {
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: 0, end: 1),
      duration: duration,
      curve: curve,
      builder: (context, value, child) {
        return Transform.scale(
          scale: scaleBegin + (1 - scaleBegin) * value,
          child: Opacity(
            opacity: value,
            child: child,
          ),
        );
      },
      child: child,
    );
  }
  
  // Page transitions
  static Route<dynamic> pageTransition({
    required Widget page,
    required RouteSettings settings,
  }) {
    return PageRouteBuilder(
      settings: settings,
      pageBuilder: (context, animation, secondaryAnimation) => page,
      transitionsBuilder: (context, animation, secondaryAnimation, child) {
        return SlideTransition(
          position: Tween<Offset>(
            begin: const Offset(1, 0),
            end: Offset.zero,
          ).animate(CurvedAnimation(
            parent: animation,
            curve: easeOut,
          )),
          child: child,
        );
      },
      transitionDuration: normal,
    );
  }
  
  static Route<dynamic> fadeTransition({
    required Widget page,
    required RouteSettings settings,
  }) {
    return PageRouteBuilder(
      settings: settings,
      pageBuilder: (context, animation, secondaryAnimation) => page,
      transitionsBuilder: (context, animation, secondaryAnimation, child) {
        return FadeTransition(
          opacity: animation,
          child: child,
        );
      },
      transitionDuration: normal,
    );
  }
  
  // Hero animations
  static Widget hero({
    required String tag,
    required Widget child,
  }) {
    return Hero(
      tag: tag,
      child: child,
    );
  }
  
  // Staggered animations helper
  static List<Widget> staggered({
    required List<Widget> children,
    Duration duration = normal,
    Curve curve = easeOut,
    int startIndex = 0,
  }) {
    return List.generate(children.length, (index) {
      final delay = Duration(
        milliseconds: (index - startIndex) * 50,
      );
      
      return TweenAnimationBuilder<double>(
        tween: Tween<double>(begin: 0, end: 1),
        duration: duration + delay,
        curve: curve,
        builder: (context, value, child) {
          return Transform.translate(
            offset: Offset(0, 20 * (1 - value)),
            child: Opacity(
              opacity: value,
              child: child,
            ),
          );
        },
        child: children[index],
      );
    });
  }
}
