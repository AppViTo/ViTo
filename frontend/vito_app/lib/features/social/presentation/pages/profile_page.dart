import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:image_picker/image_picker.dart';
import '../../../../core/di/injection_container.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../auth/presentation/bloc/auth_bloc.dart';
import '../../domain/entities/social_profile.dart';
import '../../domain/repositories/social_repository.dart';
import '../bloc/social_profile_cubit.dart';
import '../widgets/follow_button.dart';
import '../widgets/stats_card.dart';

/// Perfil social (Ola 2a, T048) con `getSocialProfile`: nombre, usuario,
/// avatar, seguidores, seguidos y "Te sigue". Sin [userId] muestra el propio
/// perfil, sin botón de seguir y con "Cerrar sesión".
class ProfilePage extends StatelessWidget {
  final String? userId;

  /// Por defecto, el repositorio registrado en `sl`. Las pruebas pasan otro.
  final SocialRepository? repository;

  /// Cada aviso recarga el perfil sin volver al indicador de carga. La
  /// pestaña Perfil lo usa al volver a ella: sigue viva en el `IndexedStack`
  /// y, si no, mostraría los contadores de la primera carga.
  final Listenable? reloadSignal;

  const ProfilePage({
    super.key,
    this.userId,
    this.repository,
    this.reloadSignal,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.grisCarbon,
      body: BlocBuilder<AuthBloc, AuthState>(
        builder: (context, authState) {
          if (authState is! AuthAuthenticated) {
            return const Center(
              child: CircularProgressIndicator(color: AppColors.coralNeon),
            );
          }
          final me = authState.user.id;
          final target = userId ?? me;
          return BlocProvider(
            key: ValueKey(target),
            create: (_) => SocialProfileCubit(
              userId: target,
              repository: repository ?? sl<SocialRepository>(),
            )..load(),
            child: _ProfileView(
              isOwn: target == me,
              reloadSignal: reloadSignal,
            ),
          );
        },
      ),
    );
  }
}

class _ProfileView extends StatefulWidget {
  final bool isOwn;
  final Listenable? reloadSignal;

  const _ProfileView({required this.isOwn, this.reloadSignal});

  @override
  State<_ProfileView> createState() => _ProfileViewState();
}

class _ProfileViewState extends State<_ProfileView> {
  String? _coverImagePath;
  final ImagePicker _picker = ImagePicker();

  @override
  void initState() {
    super.initState();
    widget.reloadSignal?.addListener(_reload);
  }

  @override
  void didUpdateWidget(covariant _ProfileView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.reloadSignal != widget.reloadSignal) {
      oldWidget.reloadSignal?.removeListener(_reload);
      widget.reloadSignal?.addListener(_reload);
    }
  }

  @override
  void dispose() {
    widget.reloadSignal?.removeListener(_reload);
    super.dispose();
  }

  void _reload() => context.read<SocialProfileCubit>().load(silent: true);

  Future<void> _pickCoverImage() async {
    final XFile? image = await _picker.pickImage(
      source: ImageSource.gallery,
      maxWidth: 1920,
      maxHeight: 1080,
      imageQuality: 85,
    );
    if (image != null) setState(() => _coverImagePath = image.path);
  }

  @override
  Widget build(BuildContext context) {
    return BlocConsumer<SocialProfileCubit, SocialProfileState>(
      listenWhen: (_, state) =>
          state.status == SocialProfileStatus.loaded && state.error != null,
      listener: (context, state) => ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(state.error!))),
      builder: (context, state) {
        switch (state.status) {
          case SocialProfileStatus.loading:
            return const Center(
              child: CircularProgressIndicator(color: AppColors.coralNeon),
            );
          case SocialProfileStatus.failure:
            return _ErrorView(
              message: state.error ?? 'No pudimos cargar el perfil, intenta de nuevo',
              onRetry: () => context.read<SocialProfileCubit>().load(),
              // Sin perfil, el propio sigue pudiendo cerrar sesión.
              footer: widget.isOwn ? const _LogoutButton() : null,
            );
          case SocialProfileStatus.loaded:
            return _buildProfile(context, state.profile!, state.followBusy);
        }
      },
    );
  }

  Widget _buildProfile(BuildContext context, SocialProfile profile, bool busy) {
    final avatar = profile.avatar;
    final label = profile.label;
    return CustomScrollView(
      slivers: [
        SliverAppBar(
          expandedHeight: 220,
          pinned: true,
          backgroundColor: AppColors.grisCarbon,
          flexibleSpace: FlexibleSpaceBar(background: _buildCover()),
        ),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            child: Column(
              children: [
                const SizedBox(height: 24),
                CircleAvatar(
                  radius: 48,
                  backgroundColor: AppColors.lavandaPale.withValues(alpha: 0.2),
                  backgroundImage: avatar != null ? NetworkImage(avatar) : null,
                  child: avatar == null
                      ? Text(
                          label.isEmpty ? '?' : label[0].toUpperCase(),
                          style: const TextStyle(
                            fontSize: 36,
                            fontWeight: FontWeight.w700,
                            color: AppColors.blancoHielo,
                          ),
                        )
                      : null,
                ),
                const SizedBox(height: 16),
                Text(
                  label,
                  style: const TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                    color: AppColors.blancoHielo,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  '@${profile.username}',
                  style: const TextStyle(
                    fontSize: 15,
                    color: AppColors.textSecondary,
                  ),
                ),
                if (!widget.isOwn && profile.isFollowedBy) ...[
                  const SizedBox(height: 8),
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceElevated,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Text(
                      'Te sigue',
                      style: TextStyle(fontSize: 12, color: AppColors.textSecondary),
                    ),
                  ),
                ],
                const SizedBox(height: 24),
                Container(
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceDark,
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      StatsCard(label: 'Seguidores', value: profile.followersCount),
                      Container(width: 1, height: 32, color: AppColors.grey700),
                      StatsCard(label: 'Siguiendo', value: profile.followingCount),
                    ],
                  ),
                ),
                const SizedBox(height: 24),
                if (widget.isOwn)
                  const _LogoutButton()
                else
                  FollowButton(
                    isFollowing: profile.isFollowing,
                    busy: busy,
                    onPressed: () =>
                        context.read<SocialProfileCubit>().toggleFollow(),
                  ),
                const SizedBox(height: 24),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildCover() {
    return GestureDetector(
      onTap: widget.isOwn ? _pickCoverImage : null,
      child: Stack(
        fit: StackFit.expand,
        children: [
          if (_coverImagePath != null)
            kIsWeb
                ? Image.network(_coverImagePath!, fit: BoxFit.cover)
                : Image.file(File(_coverImagePath!), fit: BoxFit.cover)
          else
            Container(
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [AppColors.coralNeon, AppColors.grisCarbon],
                ),
              ),
            ),
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            height: 80,
            child: Container(
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [Colors.transparent, AppColors.grisCarbon],
                ),
              ),
            ),
          ),
          if (widget.isOwn)
            Positioned(
              bottom: 24,
              right: 20,
              child: GestureDetector(
                onTap: _pickCoverImage,
                child: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: Colors.black.withValues(alpha: 0.5),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(
                    Icons.camera_alt,
                    color: AppColors.blancoHielo,
                    size: 20,
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _LogoutButton extends StatelessWidget {
  const _LogoutButton();

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: OutlinedButton.icon(
        onPressed: () => context.read<AuthBloc>().add(const LogoutRequested()),
        icon: const Icon(Icons.logout, size: 18, color: AppColors.coralNeon),
        label: const Text(
          'Cerrar sesión',
          style: TextStyle(color: AppColors.coralNeon),
        ),
        style: OutlinedButton.styleFrom(
          side: const BorderSide(color: AppColors.coralNeon),
          padding: const EdgeInsets.symmetric(vertical: 14),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  final String message;
  final VoidCallback onRetry;
  final Widget? footer;

  const _ErrorView({required this.message, required this.onRetry, this.footer});

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.person_off_outlined,
                  size: 56, color: AppColors.textMuted),
              const SizedBox(height: 16),
              Text(
                message,
                textAlign: TextAlign.center,
                style: const TextStyle(color: AppColors.blancoHielo, fontSize: 16),
              ),
              const SizedBox(height: 16),
              ElevatedButton(onPressed: onRetry, child: const Text('Reintentar')),
              if (footer != null) ...[
                const SizedBox(height: 24),
                footer!,
              ],
            ],
          ),
        ),
      ),
    );
  }
}
