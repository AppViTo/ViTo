import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:intl/intl.dart';

import '../../../../config/routes/app_router.dart';
import '../../../../core/navigation/pending_invite_link.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../auth/presentation/bloc/auth_bloc.dart';
import '../../domain/entities/invite_link.dart';
import '../bloc/invite_link_cubit.dart';

/// `/i/<token>` (T043): vista previa pública del evento y unión.
///
/// Sin sesión muestra la invitación e invita a iniciar sesión o registrarse;
/// al hacerlo, la app vuelve aquí ([PendingInviteLink]). Con sesión, "Unirme"
/// llama a `acceptInviteLink` y abre el evento.
class InviteLinkPage extends StatefulWidget {
  final String token;

  const InviteLinkPage({super.key, required this.token});

  @override
  State<InviteLinkPage> createState() => _InviteLinkPageState();
}

class _InviteLinkPageState extends State<InviteLinkPage> {
  @override
  void initState() {
    super.initState();
    context.read<InviteLinkCubit>().load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.grisCarbon,
      appBar: AppBar(
        backgroundColor: AppColors.grisCarbon,
        title: const Text('Invitación'),
      ),
      body: BlocConsumer<InviteLinkCubit, InviteLinkState>(
        listenWhen: (previous, current) =>
            current.status == InviteLinkStatus.joined ||
            (current.error != null &&
                current.status == InviteLinkStatus.preview),
        listener: _onJoinResult,
        builder: (context, state) {
          switch (state.status) {
            case InviteLinkStatus.loading:
              return const Center(
                child: CircularProgressIndicator(color: AppColors.coralNeon),
              );
            case InviteLinkStatus.unavailable:
            case InviteLinkStatus.failure:
              return _Message(
                icon: Icons.link_off,
                message: state.error ?? inviteLinkUnavailableMessage,
                onRetry: state.status == InviteLinkStatus.failure
                    ? () => context.read<InviteLinkCubit>().load()
                    : null,
              );
            case InviteLinkStatus.preview:
            case InviteLinkStatus.joined:
              return _PreviewBody(
                preview: state.preview!,
                joining: state.joining ||
                    state.status == InviteLinkStatus.joined,
                onJoin: () => context.read<InviteLinkCubit>().join(),
                onSignIn: () => _goToAuth(AppRouter.login),
                onRegister: () => _goToAuth(AppRouter.register),
              );
          }
        },
      ),
    );
  }

  void _onJoinResult(BuildContext context, InviteLinkState state) {
    final messenger = ScaffoldMessenger.of(context);
    if (state.status == InviteLinkStatus.joined) {
      final title = state.preview?.title;
      messenger.showSnackBar(SnackBar(
        content: Text(title != null ? 'Te uniste a «$title»' : 'Te uniste'),
      ));
      Navigator.pushReplacementNamed(
        context,
        AppRouter.eventDetail,
        arguments: {
          'eventId': state.invitation?.eventId ?? state.preview?.eventId,
        },
      );
      return;
    }
    messenger.showSnackBar(SnackBar(content: Text(state.error!)));
  }

  void _goToAuth(String route) {
    PendingInviteLink.remember(widget.token);
    Navigator.pushNamed(context, route);
  }
}

class _PreviewBody extends StatelessWidget {
  final InviteLinkPreview preview;
  final bool joining;
  final VoidCallback onJoin;
  final VoidCallback onSignIn;
  final VoidCallback onRegister;

  const _PreviewBody({
    required this.preview,
    required this.joining,
    required this.onJoin,
    required this.onSignIn,
    required this.onRegister,
  });

  @override
  Widget build(BuildContext context) {
    final authState = _authState(context);
    final start = preview.startDate;

    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        if (preview.coverImage != null) ...[
          ClipRRect(
            borderRadius: BorderRadius.circular(16),
            child: AspectRatio(
              aspectRatio: 16 / 9,
              child: Image.network(
                preview.coverImage!,
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) =>
                    Container(color: AppColors.surfaceDark),
              ),
            ),
          ),
          const SizedBox(height: 20),
        ],
        Text(
          '${preview.organizerDisplayName} te invita a',
          style: const TextStyle(color: AppColors.textSecondary),
        ),
        const SizedBox(height: 4),
        Text(
          preview.title,
          style: const TextStyle(
            color: AppColors.white,
            fontSize: 26,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 16),
        if (start != null)
          _InfoRow(
            icon: Icons.calendar_today,
            text: DateFormat("EEEE, d 'de' MMMM • h:mm a", 'es').format(start),
          ),
        if (preview.location != null)
          _InfoRow(icon: Icons.location_on, text: preview.location!),
        const SizedBox(height: 32),
        if (authState is AuthAuthenticated)
          ElevatedButton(
            onPressed: joining ? null : onJoin,
            child: joining
                ? const SizedBox(
                    height: 20,
                    width: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Unirme al evento'),
          )
        else if (authState is AuthInitial || authState is AuthLoading)
          const Center(
            child: CircularProgressIndicator(color: AppColors.coralNeon),
          )
        else ...[
          const Text(
            'Inicia sesión o regístrate para unirte',
            textAlign: TextAlign.center,
            style: TextStyle(color: AppColors.white, fontSize: 16),
          ),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: onSignIn,
            child: const Text('Iniciar sesión'),
          ),
          const SizedBox(height: 8),
          OutlinedButton(
            onPressed: onRegister,
            child: const Text('Crear cuenta'),
          ),
        ],
      ],
    );
  }

  /// Sin `AuthBloc` (por ejemplo, en pruebas) se trata como sin sesión.
  AuthState? _authState(BuildContext context) {
    try {
      return context.watch<AuthBloc>().state;
    } catch (_) {
      return null;
    }
  }
}

class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String text;

  const _InfoRow({required this.icon, required this.text});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        children: [
          Icon(icon, size: 18, color: AppColors.coralNeon),
          const SizedBox(width: 8),
          Expanded(
            child: Text(text, style: const TextStyle(color: AppColors.white)),
          ),
        ],
      ),
    );
  }
}

class _Message extends StatelessWidget {
  final IconData icon;
  final String message;
  final VoidCallback? onRetry;

  const _Message({required this.icon, required this.message, this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 56, color: AppColors.textMuted),
            const SizedBox(height: 16),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.white, fontSize: 16),
            ),
            if (onRetry != null) ...[
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: onRetry,
                child: const Text('Reintentar'),
              ),
            ],
            const SizedBox(height: 8),
            TextButton(
              onPressed: () => Navigator.of(context).pushNamedAndRemoveUntil(
                AppRouter.initialRoute,
                (_) => false,
              ),
              child: const Text('Ir a ViTo'),
            ),
          ],
        ),
      ),
    );
  }
}
