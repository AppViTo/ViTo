import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../data/models/home_models.dart';

class ActiveFriendsSection extends StatelessWidget {
  final List<ActiveFriend> friends;

  const ActiveFriendsSection({
    super.key,
    required this.friends,
  });

  @override
  Widget build(BuildContext context) {
    final onlineFriends = friends.where((f) => f.isOnline).toList();
    if (onlineFriends.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 8, 24, 12),
          child: Row(
            children: [
              Container(
                width: 8,
                height: 8,
                decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  color: AppColors.online,
                ),
              ),
              const SizedBox(width: 8),
              const Text(
                'Amigos activos',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: AppColors.blancoHielo,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                '${onlineFriends.length}',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: AppColors.textSecondary.withValues(alpha: 0.7),
                ),
              ),
            ],
          ),
        ),
        SizedBox(
          height: 90,
          child: ListView.separated(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            scrollDirection: Axis.horizontal,
            itemCount: onlineFriends.length,
            separatorBuilder: (_, __) => const SizedBox(width: 16),
            itemBuilder: (context, index) {
              return _buildFriendAvatar(onlineFriends[index]);
            },
          ),
        ),
      ],
    );
  }

  Widget _buildFriendAvatar(ActiveFriend friend) {
    return Column(
      children: [
        Stack(
          clipBehavior: Clip.none,
          children: [
            Container(
              width: 52,
              height: 52,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: LinearGradient(
                  colors: [
                    AppColors.lavandaPale.withValues(alpha: 0.3),
                    AppColors.coralNeon.withValues(alpha: 0.3),
                  ],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                border: Border.all(
                  color: AppColors.online,
                  width: 2,
                ),
              ),
              child: Center(
                child: Text(
                  friend.displayName[0].toUpperCase(),
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                    color: AppColors.blancoHielo,
                  ),
                ),
              ),
            ),
            Positioned(
              bottom: 0,
              right: 0,
              child: Container(
                width: 14,
                height: 14,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: AppColors.online,
                  border: Border.all(
                    color: const Color(0xFF161D2F),
                    width: 2,
                  ),
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 6),
        Text(
          friend.displayName,
          style: TextStyle(
            fontSize: 11,
            color: AppColors.textSecondary.withValues(alpha: 0.8),
          ),
        ),
        if (friend.currentActivity != null)
          Text(
            friend.currentActivity!,
            style: const TextStyle(
              fontSize: 9,
              color: AppColors.coralNeon,
              fontWeight: FontWeight.w500,
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
      ],
    );
  }
}
