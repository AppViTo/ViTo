import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../../core/theme/app_colors.dart';
import '../../domain/entities/user_summary.dart';
import '../bloc/follow_list_cubit.dart';

/// Fila de un usuario: avatar (o inicial), nombre y `@usuario`.
class UserTile extends StatelessWidget {
  final UserSummary user;
  final VoidCallback? onTap;

  const UserTile({super.key, required this.user, this.onTap});

  @override
  Widget build(BuildContext context) {
    final avatar = user.avatar;
    final label = user.label;
    return ListTile(
      onTap: onTap,
      leading: CircleAvatar(
        backgroundColor: AppColors.lavandaPale.withValues(alpha: 0.2),
        backgroundImage: avatar != null ? NetworkImage(avatar) : null,
        child: avatar == null
            ? Text(
                label.isEmpty ? '?' : label[0].toUpperCase(),
                style: const TextStyle(color: AppColors.blancoHielo),
              )
            : null,
      ),
      title: Text(label, style: const TextStyle(color: AppColors.blancoHielo)),
      subtitle: Text(
        '@${user.username}',
        style: const TextStyle(color: AppColors.textSecondary),
      ),
    );
  }
}

/// Elementos antes del final a partir de los cuales se pide la página
/// siguiente.
const _loadMoreThreshold = 5;

/// Lista paginada de [FollowListCubit] (T053): pide la página siguiente al
/// acercarse al final y se detiene con la primera página vacía.
class FollowListView extends StatelessWidget {
  final String emptyMessage;
  final ValueChanged<UserSummary> onUserTap;

  const FollowListView({
    super.key,
    required this.emptyMessage,
    required this.onUserTap,
  });

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<FollowListCubit, FollowListState>(
      builder: (context, state) {
        if (state.loading) {
          return const Center(
            child: CircularProgressIndicator(color: AppColors.coralNeon),
          );
        }
        if (state.items.isEmpty) {
          return _Message(
            message: state.error ?? emptyMessage,
            onRetry: state.error != null
                ? () => context.read<FollowListCubit>().load()
                : null,
          );
        }
        final showFooter = state.loadingMore || state.error != null;
        final cubit = context.read<FollowListCubit>();
        return ListView.builder(
          physics: const AlwaysScrollableScrollPhysics(),
          itemCount: state.items.length + (showFooter ? 1 : 0),
          itemBuilder: (context, index) {
            if (index < state.items.length) {
              // ListView.builder construye los elementos al acercarse a ellos:
              // al construir uno de los últimos se pide la página siguiente.
              // Así también sigue una primera página que no llena la
              // pantalla. Tras un error se espera a "Reintentar".
              if (state.error == null &&
                  index >= state.items.length - _loadMoreThreshold) {
                WidgetsBinding.instance.addPostFrameCallback((_) {
                  if (cubit.canLoadMore) cubit.loadMore();
                });
              }
              final user = state.items[index];
              return UserTile(user: user, onTap: () => onUserTap(user));
            }
            if (state.loadingMore) {
              return const Padding(
                padding: EdgeInsets.all(16),
                child: Center(
                  child: CircularProgressIndicator(color: AppColors.coralNeon),
                ),
              );
            }
            return TextButton(
              onPressed: cubit.loadMore,
              child: Text('${state.error} · Reintentar'),
            );
          },
        );
      },
    );
  }
}

class _Message extends StatelessWidget {
  final String message;
  final VoidCallback? onRetry;

  const _Message({required this.message, this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.people_outline_rounded,
              size: 48,
              color: AppColors.textSecondary.withValues(alpha: 0.3),
            ),
            const SizedBox(height: 12),
            Text(
              message,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 14,
                color: AppColors.textSecondary.withValues(alpha: 0.7),
              ),
            ),
            if (onRetry != null) ...[
              const SizedBox(height: 12),
              ElevatedButton(onPressed: onRetry, child: const Text('Reintentar')),
            ],
          ],
        ),
      ),
    );
  }
}
