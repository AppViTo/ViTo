import 'package:flutter/material.dart';

import '../../../../core/theme/app_colors.dart';

/// Botón de seguir de un perfil ajeno (T042). El estado lo lleva
/// `SocialProfileCubit`, que lo actualiza de forma optimista; mientras la
/// petición está en curso el botón no acepta otro toque.
class FollowButton extends StatelessWidget {
  final bool isFollowing;
  final bool busy;
  final VoidCallback onPressed;

  const FollowButton({
    super.key,
    required this.isFollowing,
    required this.onPressed,
    this.busy = false,
  });

  @override
  Widget build(BuildContext context) {
    final shape = RoundedRectangleBorder(borderRadius: BorderRadius.circular(12));
    const padding = EdgeInsets.symmetric(vertical: 14);
    return SizedBox(
      width: double.infinity,
      child: isFollowing
          ? OutlinedButton.icon(
              onPressed: busy ? null : onPressed,
              icon: const Icon(Icons.check, size: 18),
              label: const Text('Siguiendo'),
              style: OutlinedButton.styleFrom(
                foregroundColor: AppColors.blancoHielo,
                side: const BorderSide(color: AppColors.grey700),
                padding: padding,
                shape: shape,
              ),
            )
          : ElevatedButton.icon(
              onPressed: busy ? null : onPressed,
              icon: const Icon(Icons.person_add_outlined, size: 18),
              label: const Text('Seguir'),
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.coralNeon,
                foregroundColor: AppColors.blancoHielo,
                padding: padding,
                shape: shape,
              ),
            ),
    );
  }
}
