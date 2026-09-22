import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../data/models/home_models.dart';

class StoryCard extends StatelessWidget {
  final Story story;
  final VoidCallback? onTap;

  const StoryCard({
    super.key,
    required this.story,
    this.onTap,
  });

  Color _getAvatarColor() {
    final colors = [
      AppColors.coralNeon,
      AppColors.lavandaPale,
      AppColors.eventBlue,
      AppColors.eventGreen,
      AppColors.eventPurple,
    ];
    return colors[story.userName.hashCode % colors.length];
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Container(
            width: double.infinity,
            height: double.infinity,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(16),
              gradient: LinearGradient(
                colors: [
                  _getAvatarColor().withValues(alpha: 0.3),
                  _getAvatarColor().withValues(alpha: 0.1),
                ],
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
              ),
              border: Border.all(
                color: story.isViewed
                    ? AppColors.grey800.withValues(alpha: 0.5)
                    : AppColors.coralNeon.withValues(alpha: 0.6),
                width: 2,
              ),
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: story.isViewed ? null : AppColors.primaryGradient,
                    color: story.isViewed ? AppColors.grey800 : null,
                  ),
                  child: Center(
                    child: Text(
                      story.userName[0].toUpperCase(),
                      style: const TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w700,
                        color: AppColors.blancoHielo,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: Text(
                    story.userName,
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                      color: story.isViewed
                          ? AppColors.textSecondary
                          : AppColors.blancoHielo,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    textAlign: TextAlign.center,
                  ),
                ),
              ],
            ),
          ),
          Positioned(
            top: 6,
            left: 6,
            child: Container(
              width: 28,
              height: 28,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: _getAvatarColor(),
                border: Border.all(
                  color: story.isViewed
                      ? AppColors.grey800
                      : AppColors.coralNeon,
                  width: 2,
                ),
              ),
              child: Center(
                child: Text(
                  story.userName[0].toUpperCase(),
                  style: const TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    color: AppColors.blancoHielo,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
