import 'package:flutter/material.dart';

import '../../../../core/theme/app_colors.dart';

/// Un contador del perfil social (seguidores o seguidos, T048).
class StatsCard extends StatelessWidget {
  final String label;
  final int value;

  const StatsCard({
    super.key,
    required this.label,
    required this.value,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(
          value.toString(),
          style: const TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.w700,
            color: AppColors.blancoHielo,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          label,
          style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
        ),
      ],
    );
  }
}
