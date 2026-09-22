import 'package:flutter/material.dart';
import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';

enum AppAvatarSize { xs, sm, md, lg, xl, xxl }

class AppAvatar extends StatelessWidget {
  final String? imageUrl;
  final String? initials;
  final AppAvatarSize size;
  final Color? backgroundColor;
  final Color? textColor;
  final bool isOnline;
  final bool showBorder;
  final Color? borderColor;
  final VoidCallback? onTap;
  final Widget? badge;
  
  const AppAvatar({
    super.key,
    this.imageUrl,
    this.initials,
    this.size = AppAvatarSize.md,
    this.backgroundColor,
    this.textColor,
    this.isOnline = false,
    this.showBorder = false,
    this.borderColor,
    this.onTap,
    this.badge,
  });
  
  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Container(
            width: _getSize(),
            height: _getSize(),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: backgroundColor ?? AppColors.primary,
              border: showBorder
                  ? Border.all(
                      color: borderColor ?? AppColors.white,
                      width: 2,
                    )
                  : null,
              image: imageUrl != null
                  ? DecorationImage(
                      image: NetworkImage(imageUrl!),
                      fit: BoxFit.cover,
                      onError: (error, stackTrace) {
                        // Error handling is done by the image widget
                      },
                    )
                  : null,
            ),
            child: imageUrl == null
                ? Center(
                    child: Text(
                      _getInitials(),
                      style: TextStyle(
                        color: textColor ?? AppColors.white,
                        fontWeight: FontWeight.w600,
                        fontSize: _getFontSize(),
                      ),
                    ),
                  )
                : null,
          ),
          
          if (isOnline)
            Positioned(
              right: 0,
              bottom: 0,
              child: Container(
                width: _getOnlineIndicatorSize(),
                height: _getOnlineIndicatorSize(),
                decoration: BoxDecoration(
                  color: AppColors.online,
                  shape: BoxShape.circle,
                  border: Border.all(
                    color: AppColors.white,
                    width: 2,
                  ),
                ),
              ),
            ),
          
          if (badge != null)
            Positioned(
              right: -4,
              top: -4,
              child: badge!,
            ),
        ],
      ),
    );
  }
  
  String _getInitials() {
    if (initials != null && initials!.isNotEmpty) {
      return initials!;
    }
    
    if (imageUrl != null) {
      return '?';
    }
    
    return '?';
  }
  
  double _getSize() {
    switch (size) {
      case AppAvatarSize.xs:
        return AppSpacing.avatarXs;
      case AppAvatarSize.sm:
        return AppSpacing.avatarSm;
      case AppAvatarSize.md:
        return AppSpacing.avatarMd;
      case AppAvatarSize.lg:
        return AppSpacing.avatarLg;
      case AppAvatarSize.xl:
        return AppSpacing.avatarXl;
      case AppAvatarSize.xxl:
        return AppSpacing.avatarXxl;
    }
  }
  
  double _getFontSize() {
    switch (size) {
      case AppAvatarSize.xs:
        return 8;
      case AppAvatarSize.sm:
        return 10;
      case AppAvatarSize.md:
        return 12;
      case AppAvatarSize.lg:
        return 14;
      case AppAvatarSize.xl:
        return 16;
      case AppAvatarSize.xxl:
        return 20;
    }
  }
  
  double _getOnlineIndicatorSize() {
    switch (size) {
      case AppAvatarSize.xs:
        return 8;
      case AppAvatarSize.sm:
        return 10;
      case AppAvatarSize.md:
        return 12;
      case AppAvatarSize.lg:
        return 14;
      case AppAvatarSize.xl:
        return 16;
      case AppAvatarSize.xxl:
        return 20;
    }
  }
}

class AppAvatarGroup extends StatelessWidget {
  final List<AppAvatar> avatars;
  final int maxCount;
  final AppAvatarSize size;
  
  const AppAvatarGroup({
    super.key,
    required this.avatars,
    this.maxCount = 3,
    this.size = AppAvatarSize.sm,
  });
  
  @override
  Widget build(BuildContext context) {
    final displayAvatars = avatars.take(maxCount).toList();
    final remainingCount = avatars.length - maxCount;
    
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        ...displayAvatars.asMap().entries.map((entry) {
          final index = entry.key;
          final avatar = entry.value;
          
          return Transform.translate(
            offset: Offset(-index * 8.0, 0),
            child: avatar,
          );
        }),
        
        if (remainingCount > 0)
          Transform.translate(
            offset: Offset(-maxCount * 8.0, 0),
            child: Container(
              width: _getSize(),
              height: _getSize(),
              decoration: BoxDecoration(
                color: AppColors.grey300,
                shape: BoxShape.circle,
                border: Border.all(
                  color: AppColors.white,
                  width: 2,
                ),
              ),
              child: Center(
                child: Text(
                  '+$remainingCount',
                  style: TextStyle(
                    color: AppColors.grey700,
                    fontWeight: FontWeight.w600,
                    fontSize: _getFontSize(),
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }
  
  double _getSize() {
    switch (size) {
      case AppAvatarSize.xs:
        return AppSpacing.avatarXs;
      case AppAvatarSize.sm:
        return AppSpacing.avatarSm;
      case AppAvatarSize.md:
        return AppSpacing.avatarMd;
      case AppAvatarSize.lg:
        return AppSpacing.avatarLg;
      case AppAvatarSize.xl:
        return AppSpacing.avatarXl;
      case AppAvatarSize.xxl:
        return AppSpacing.avatarXxl;
    }
  }
  
  double _getFontSize() {
    switch (size) {
      case AppAvatarSize.xs:
        return 8;
      case AppAvatarSize.sm:
        return 10;
      case AppAvatarSize.md:
        return 12;
      case AppAvatarSize.lg:
        return 14;
      case AppAvatarSize.xl:
        return 16;
      case AppAvatarSize.xxl:
        return 20;
    }
  }
}
