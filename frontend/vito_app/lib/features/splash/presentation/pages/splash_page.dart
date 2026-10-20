import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../auth/presentation/bloc/auth_bloc.dart';

class SplashPage extends StatefulWidget {
  const SplashPage({super.key});

  @override
  State<SplashPage> createState() => _SplashPageState();
}

class _SplashPageState extends State<SplashPage> with TickerProviderStateMixin {
  late AnimationController _logoController;
  late AnimationController _textController;
  late AnimationController _loadingController;
  late Animation<double> _logoScale;
  late Animation<double> _logoOpacity;
  late Animation<double> _textOpacity;
  late Animation<Offset> _textSlide;
  late Animation<double> _loadingWidth;
  bool _navigated = false;
  StreamSubscription<AuthState>? _authSub;

  @override
  void initState() {
    super.initState();

    // Logo animation
    _logoController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    );
    _logoScale = Tween<double>(begin: 0.5, end: 1.0).animate(
      CurvedAnimation(parent: _logoController, curve: Curves.easeOutBack),
    );
    _logoOpacity = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _logoController, curve: Curves.easeOut),
    );

    // Text animation
    _textController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 600),
    );
    _textOpacity = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _textController, curve: Curves.easeOut),
    );
    _textSlide = Tween<Offset>(
      begin: const Offset(0, 0.3),
      end: Offset.zero,
    ).animate(CurvedAnimation(parent: _textController, curve: Curves.easeOutCubic));

    // Loading bar animation
    _loadingController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2000),
    );
    _loadingWidth = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _loadingController, curve: Curves.easeInOut),
    );

    _authSub = context.read<AuthBloc>().stream.listen(_maybeGo);

    _startAnimation();
  }

  Future<void> _startAnimation() async {
    await Future.delayed(const Duration(milliseconds: 300));
    _logoController.forward();

    await Future.delayed(const Duration(milliseconds: 500));
    _textController.forward();

    await Future.delayed(const Duration(milliseconds: 300));
    _loadingController.forward();

    await Future.delayed(const Duration(milliseconds: 2500));
    if (mounted) {
      _maybeGo(context.read<AuthBloc>().state);
    }
  }

  /// Decide la primera pantalla cuando el `AuthCheckRequested` de `ViToApp`
  /// terminó: sesión restaurada → home, sin sesión (o refresh rechazado) → login.
  void _maybeGo(AuthState state) {
    if (_navigated || !mounted) return;

    if (state is AuthAuthenticated) {
      _navigated = true;
      Navigator.pushReplacementNamed(context, '/home');
    } else if (state is AuthUnauthenticated) {
      _navigated = true;
      Navigator.pushReplacementNamed(context, '/login');
    }
  }

  @override
  void dispose() {
    _authSub?.cancel();
    _logoController.dispose();
    _textController.dispose();
    _loadingController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.grisCarbon,
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Spacer(flex: 3),

            // Logo
            AnimatedBuilder(
              animation: _logoController,
              builder: (context, child) {
                return Opacity(
                  opacity: _logoOpacity.value,
                  child: Transform.scale(
                    scale: _logoScale.value,
                    child: child,
                  ),
                );
              },
              child: Container(
                width: 140,
                height: 140,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: AppColors.coralNeon.withValues(alpha: 0.1),
                  border: Border.all(
                    color: AppColors.coralNeon.withValues(alpha: 0.3),
                    width: 2,
                  ),
                ),
                child: ClipOval(
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Image.asset(
                      'assets/images/logoViTo.png',
                      fit: BoxFit.contain,
                    ),
                  ),
                ),
              ),
            ),

            const SizedBox(height: 32),

            // Brand name
            SlideTransition(
              position: _textSlide,
              child: FadeTransition(
                opacity: _textOpacity,
                child: const Text(
                  'ViTo',
                  style: TextStyle(
                    fontSize: 48,
                    fontWeight: FontWeight.w800,
                    color: AppColors.blancoHielo,
                    letterSpacing: 4,
                  ),
                ),
              ),
            ),

            const SizedBox(height: 12),

            // Slogan
            SlideTransition(
              position: _textSlide,
              child: FadeTransition(
                opacity: _textOpacity,
                child: Text(
                  'Vive la vibra, guarda el momento.',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w400,
                    color: AppColors.lavandaPale.withValues(alpha: 0.8),
                    letterSpacing: 1.5,
                  ),
                ),
              ),
            ),

            const Spacer(flex: 3),

            // Loading bar
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 80),
              child: AnimatedBuilder(
                animation: _loadingController,
                builder: (context, child) {
                  return Column(
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(4),
                        child: LinearProgressIndicator(
                          value: _loadingWidth.value,
                          backgroundColor: AppColors.grey800,
                          valueColor: const AlwaysStoppedAnimation<Color>(AppColors.coralNeon),
                          minHeight: 3,
                        ),
                      ),
                      const SizedBox(height: 16),
                      Text(
                        'Preparando tu experiencia...',
                        style: TextStyle(
                          fontSize: 12,
                          color: AppColors.textSecondary.withValues(alpha: 0.6),
                        ),
                      ),
                    ],
                  );
                },
              ),
            ),

            const SizedBox(height: 60),
          ],
        ),
      ),
    );
  }
}
