import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../auth/presentation/bloc/auth_bloc.dart';

class PeopleScreen extends StatefulWidget {
  const PeopleScreen({super.key});

  @override
  State<PeopleScreen> createState() => _PeopleScreenState();
}

enum PeopleFilter { amigos, seguidos, sugerencias }

/// El grafo social (amigos, seguidos, sugerencias) aún no está en el contrato
/// (Ola 2, T-F02). Hasta entonces la pantalla muestra estados vacíos: nunca
/// datos de ejemplo, que se le mostraban a cualquier usuario (Bug-F06).
class _PeopleScreenState extends State<PeopleScreen> {
  PeopleFilter _selectedFilter = PeopleFilter.amigos;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.grisCarbon,
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildHeader(),
            _buildSearchBar(),
            _buildFilterBar(),
            Expanded(child: _buildContent()),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    final authState = context.read<AuthBloc>().state;
    String userName = '';
    if (authState is AuthAuthenticated) {
      userName = authState.user.profile?.displayName ?? authState.user.username;
    }

    return Container(
      padding: const EdgeInsets.fromLTRB(24, 20, 24, 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          const Text(
            'Tu Gente',
            style: TextStyle(
              fontSize: 28,
              fontWeight: FontWeight.w800,
              color: AppColors.blancoHielo,
            ),
          ),
          Text(
            userName,
            style: const TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w500,
              color: AppColors.coralNeon,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSearchBar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 8, 24, 12),
      child: Container(
        height: 44,
        decoration: BoxDecoration(
          color: AppColors.surfaceDark,
          borderRadius: BorderRadius.circular(12),
        ),
        child: const TextField(
          decoration: InputDecoration(
            hintText: 'Buscar personas...',
            hintStyle: TextStyle(color: AppColors.textHint, fontSize: 14),
            prefixIcon: Icon(Icons.search_rounded, size: 20, color: AppColors.textHint),
            border: InputBorder.none,
            contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          ),
          style: TextStyle(color: AppColors.blancoHielo, fontSize: 14),
        ),
      ),
    );
  }

  Widget _buildFilterBar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 0, 24, 12),
      child: Row(
        children: [
          Expanded(child: _buildFilterChip('Amigos', PeopleFilter.amigos)),
          const SizedBox(width: 8),
          Expanded(child: _buildFilterChip('Seguidos', PeopleFilter.seguidos)),
          const SizedBox(width: 8),
          Expanded(child: _buildFilterChip('Sugerencias', PeopleFilter.sugerencias)),
        ],
      ),
    );
  }

  Widget _buildFilterChip(String label, PeopleFilter filter) {
    final isSelected = _selectedFilter == filter;
    return GestureDetector(
      onTap: () {
        setState(() {
          _selectedFilter = filter;
        });
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: BoxDecoration(
          color: isSelected ? AppColors.coralNeon : AppColors.surfaceDark,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: isSelected ? AppColors.coralNeon : AppColors.grey700,
            width: 1,
          ),
        ),
        child: Center(
          child: Text(
            label,
            style: TextStyle(
              fontSize: 13,
              fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
              color: isSelected ? AppColors.blancoHielo : AppColors.textSecondary,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildContent() {
    final (icon, message) = switch (_selectedFilter) {
      PeopleFilter.amigos => (Icons.people_outline_rounded, 'Aún no tienes amigos'),
      PeopleFilter.seguidos => (Icons.person_add_alt_rounded, 'Aún no sigues a nadie'),
      PeopleFilter.sugerencias => (Icons.explore_outlined, 'Aún no hay sugerencias para ti'),
    };

    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
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
          ],
        ),
      ),
    );
  }
}
