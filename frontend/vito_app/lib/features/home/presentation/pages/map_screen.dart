import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';

class MapScreen extends StatelessWidget {
  const MapScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.grisCarbon,
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(),
            Expanded(
              child: Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Container(
                      width: 100,
                      height: 100,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: AppColors.coralNeon.withValues(alpha: 0.1),
                        border: Border.all(
                          color: AppColors.coralNeon.withValues(alpha: 0.2),
                          width: 2,
                        ),
                      ),
                      child: Icon(
                        Icons.map_rounded,
                        size: 48,
                        color: AppColors.coralNeon.withValues(alpha: 0.6),
                      ),
                    ),
                    const SizedBox(height: 24),
                    const Text(
                      'Mapa de Eventos',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w700,
                        color: AppColors.blancoHielo,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Próximamente podrás ver eventos\ncerca de ti en el mapa',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 14,
                        color: AppColors.textSecondary.withValues(alpha: 0.7),
                        height: 1.5,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.fromLTRB(24, 20, 24, 16),
      child: const Row(
        children: [
          Text(
            'Mapa',
            style: TextStyle(
              fontSize: 28,
              fontWeight: FontWeight.w800,
              color: AppColors.blancoHielo,
            ),
          ),
        ],
      ),
    );
  }
}
