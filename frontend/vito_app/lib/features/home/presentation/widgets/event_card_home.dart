import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../../../core/theme/app_colors.dart';
import '../../data/models/home_models.dart';

class EventCardHome extends StatelessWidget {
  final HomeEvent event;
  final VoidCallback? onTap;

  const EventCardHome({
    super.key,
    required this.event,
    this.onTap,
  });

  Color _getCategoryColor() {
    switch (event.category) {
      case 'party':
        return AppColors.coralNeon;
      case 'music':
        return AppColors.eventPurple;
      case 'wedding':
        return AppColors.eventPink;
      case 'social':
        return AppColors.eventBlue;
      default:
        return AppColors.coralNeon;
    }
  }

  IconData _getCategoryIcon() {
    switch (event.category) {
      case 'party':
        return Icons.celebration_rounded;
      case 'music':
        return Icons.music_note_rounded;
      case 'wedding':
        return Icons.favorite_rounded;
      case 'social':
        return Icons.people_rounded;
      default:
        return Icons.event_rounded;
    }
  }

  @override
  Widget build(BuildContext context) {
    final categoryColor = _getCategoryColor();

    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 260,
        decoration: BoxDecoration(
          color: const Color(0xFF161D2F),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: AppColors.grey800.withValues(alpha: 0.3),
            width: 1,
          ),
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              _buildCoverImage(categoryColor),
              Flexible(
                child: Padding(
                  padding: const EdgeInsets.all(10),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      _buildCategoryBadge(categoryColor),
                      const SizedBox(height: 6),
                      _buildTitle(),
                      const SizedBox(height: 6),
                      _buildDateLocation(),
                      const SizedBox(height: 8),
                      _buildFooter(),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildCoverImage(Color categoryColor) {
    return Container(
      height: 100,
      decoration: BoxDecoration(
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
        gradient: LinearGradient(
          colors: [
            categoryColor.withValues(alpha: 0.3),
            categoryColor.withValues(alpha: 0.1),
          ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
      ),
      child: Stack(
        children: [
          Center(
            child: Icon(
              _getCategoryIcon(),
              size: 36,
              color: categoryColor.withValues(alpha: 0.4),
            ),
          ),
          if (event.isLive)
            Positioned(
              top: 8,
              right: 8,
              child: _buildLiveBadge(),
            ),
          Positioned(
            bottom: 8,
            left: 8,
            child: _buildGuestAvatars(),
          ),
        ],
      ),
    );
  }

  Widget _buildLiveBadge() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: AppColors.coralNeon,
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(
            color: AppColors.coralNeon.withValues(alpha: 0.4),
            blurRadius: 8,
          ),
        ],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 6,
            height: 6,
            decoration: const BoxDecoration(
              shape: BoxShape.circle,
              color: AppColors.blancoHielo,
            ),
          ),
          const SizedBox(width: 5),
          const Text(
            'EN VIVO',
            style: TextStyle(
              fontSize: 9,
              fontWeight: FontWeight.w800,
              color: AppColors.blancoHielo,
              letterSpacing: 0.5,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildGuestAvatars() {
    final guestsToShow = event.guests.take(3).toList();
    if (guestsToShow.isEmpty) return const SizedBox.shrink();

    return SizedBox(
      height: 24,
      width: 60,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          for (int i = 0; i < guestsToShow.length; i++)
            Positioned(
              left: i * 16.0,
              child: Container(
                width: 24,
                height: 24,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: _getAvatarColor(i),
                  border: Border.all(
                    color: const Color(0xFF161D2F),
                    width: 2,
                  ),
                ),
                child: Center(
                  child: Text(
                    guestsToShow[i].displayName[0].toUpperCase(),
                    style: const TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.w600,
                      color: AppColors.blancoHielo,
                    ),
                  ),
                ),
              ),
            ),
          if (event.attendeeCount > 3)
            Positioned(
              left: 48.0,
              child: Container(
                width: 24,
                height: 24,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: AppColors.grey800,
                  border: Border.all(
                    color: const Color(0xFF161D2F),
                    width: 2,
                  ),
                ),
                child: Center(
                  child: Text(
                    '+${event.attendeeCount - 3}',
                    style: const TextStyle(
                      fontSize: 8,
                      fontWeight: FontWeight.w700,
                      color: AppColors.textSecondary,
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildCategoryBadge(Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        event.category.toUpperCase(),
        style: TextStyle(
          fontSize: 9,
          fontWeight: FontWeight.w700,
          color: color,
          letterSpacing: 0.5,
        ),
      ),
    );
  }

  Widget _buildTitle() {
    return Text(
      event.title,
      style: const TextStyle(
        fontSize: 14,
        fontWeight: FontWeight.w700,
        color: AppColors.blancoHielo,
      ),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
    );
  }

  Widget _buildDateLocation() {
    return Column(
      children: [
        Row(
          children: [
            Icon(Icons.calendar_today_rounded, size: 11, color: AppColors.textSecondary.withValues(alpha: 0.6)),
            const SizedBox(width: 4),
            Expanded(
              child: Text(
                DateFormat('EEE d MMM • HH:mm', 'es').format(event.date),
                style: TextStyle(
                  fontSize: 10,
                  color: AppColors.textSecondary.withValues(alpha: 0.7),
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
        if (event.location != null) ...[
          const SizedBox(height: 3),
          Row(
            children: [
              Icon(Icons.location_on_outlined, size: 11, color: AppColors.textSecondary.withValues(alpha: 0.6)),
              const SizedBox(width: 4),
              Expanded(
                child: Text(
                  event.location!,
                  style: TextStyle(
                    fontSize: 10,
                    color: AppColors.textSecondary.withValues(alpha: 0.7),
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
        ],
      ],
    );
  }

  Widget _buildFooter() {
    return Row(
      children: [
        Icon(Icons.people_outline, size: 12, color: AppColors.textSecondary.withValues(alpha: 0.6)),
        const SizedBox(width: 3),
        Text(
          '${event.attendeeCount} asistentes',
          style: TextStyle(
            fontSize: 10,
            color: AppColors.textSecondary.withValues(alpha: 0.7),
          ),
        ),
        const Spacer(),
        Icon(Icons.arrow_forward_ios_rounded, size: 10, color: AppColors.coralNeon.withValues(alpha: 0.6)),
      ],
    );
  }

  Color _getAvatarColor(int index) {
    const colors = [
      AppColors.coralNeon,
      AppColors.lavandaPale,
      AppColors.eventBlue,
      AppColors.eventGreen,
    ];
    return colors[index % colors.length];
  }
}
