import 'package:flutter/material.dart';
import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import '../theme/app_typography.dart';
import 'app_button.dart';

enum AppEmptyType { noData, noResults, noEvents, noMedia, noComments, custom }

class AppEmpty extends StatelessWidget {
  final AppEmptyType type;
  final String? title;
  final String? message;
  final IconData? icon;
  final Widget? action;
  final VoidCallback? onAction;
  final String actionText;
  
  const AppEmpty({
    super.key,
    this.type = AppEmptyType.noData,
    this.title,
    this.message,
    this.icon,
    this.action,
    this.onAction,
    this.actionText = 'Get Started',
  });
  
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xxl),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            _buildIcon(),
            const SizedBox(height: AppSpacing.xl),
            Text(
              title ?? _getTitle(),
              style: AppTypography.h5,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(
              message ?? _getMessage(),
              style: AppTypography.bodyMedium.copyWith(
                color: AppColors.grey600,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: AppSpacing.xl),
            if (action != null)
              action!
            else if (onAction != null)
              AppButton(
                text: actionText,
                onPressed: onAction,
                icon: Icons.add,
              ),
          ],
        ),
      ),
    );
  }
  
  Widget _buildIcon() {
    IconData iconData;
    
    switch (type) {
      case AppEmptyType.noData:
        iconData = Icons.inbox_outlined;
        break;
      case AppEmptyType.noResults:
        iconData = Icons.search_off;
        break;
      case AppEmptyType.noEvents:
        iconData = Icons.event_busy;
        break;
      case AppEmptyType.noMedia:
        iconData = Icons.photo_library_outlined;
        break;
      case AppEmptyType.noComments:
        iconData = Icons.chat_bubble_outline;
        break;
      case AppEmptyType.custom:
        iconData = icon ?? Icons.inbox_outlined;
        break;
    }
    
    return Container(
      width: 80,
      height: 80,
      decoration: BoxDecoration(
        color: AppColors.grey100,
        shape: BoxShape.circle,
      ),
      child: Icon(
        iconData,
        size: 48,
        color: AppColors.grey400,
      ),
    );
  }
  
  String _getTitle() {
    switch (type) {
      case AppEmptyType.noData:
        return 'No Data';
      case AppEmptyType.noResults:
        return 'No Results';
      case AppEmptyType.noEvents:
        return 'No Events';
      case AppEmptyType.noMedia:
        return 'No Media';
      case AppEmptyType.noComments:
        return 'No Comments';
      case AppEmptyType.custom:
        return 'Empty';
    }
  }
  
  String _getMessage() {
    switch (type) {
      case AppEmptyType.noData:
        return 'There\'s nothing here yet. Start by adding some content.';
      case AppEmptyType.noResults:
        return 'We couldn\'t find any results matching your search. Try different keywords.';
      case AppEmptyType.noEvents:
        return 'No events found. Create your first event to get started.';
      case AppEmptyType.noMedia:
        return 'No photos or videos yet. Share some memories from your event.';
      case AppEmptyType.noComments:
        return 'No comments yet. Be the first to share your thoughts.';
      case AppEmptyType.custom:
        return 'Nothing to show here.';
    }
  }
}
