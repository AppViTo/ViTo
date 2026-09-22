import 'package:flutter/material.dart';
import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import '../theme/app_typography.dart';
import 'app_button.dart';

enum AppErrorType { network, server, notFound, unauthorized, generic }

class AppError extends StatelessWidget {
  final AppErrorType type;
  final String? message;
  final String? title;
  final VoidCallback? onRetry;
  final String retryText;
  final Widget? action;
  
  const AppError({
    super.key,
    this.type = AppErrorType.generic,
    this.message,
    this.title,
    this.onRetry,
    this.retryText = 'Retry',
    this.action,
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
            if (onRetry != null)
              AppButton(
                text: retryText,
                onPressed: onRetry,
                icon: Icons.refresh,
              ),
            if (action != null) ...[
              const SizedBox(height: AppSpacing.md),
              action!,
            ],
          ],
        ),
      ),
    );
  }
  
  Widget _buildIcon() {
    IconData iconData;
    Color iconColor;
    
    switch (type) {
      case AppErrorType.network:
        iconData = Icons.wifi_off;
        iconColor = AppColors.warning;
        break;
      case AppErrorType.server:
        iconData = Icons.error_outline;
        iconColor = AppColors.error;
        break;
      case AppErrorType.notFound:
        iconData = Icons.search_off;
        iconColor = AppColors.grey500;
        break;
      case AppErrorType.unauthorized:
        iconData = Icons.lock_outline;
        iconColor = AppColors.warning;
        break;
      case AppErrorType.generic:
        iconData = Icons.error_outline;
        iconColor = AppColors.error;
        break;
    }
    
    return Container(
      width: 80,
      height: 80,
      decoration: BoxDecoration(
        color: iconColor.withOpacity(0.1),
        shape: BoxShape.circle,
      ),
      child: Icon(
        iconData,
        size: 48,
        color: iconColor,
      ),
    );
  }
  
  String _getTitle() {
    switch (type) {
      case AppErrorType.network:
        return 'Connection Error';
      case AppErrorType.server:
        return 'Server Error';
      case AppErrorType.notFound:
        return 'Not Found';
      case AppErrorType.unauthorized:
        return 'Access Denied';
      case AppErrorType.generic:
        return 'Something Went Wrong';
    }
  }
  
  String _getMessage() {
    switch (type) {
      case AppErrorType.network:
        return 'Please check your internet connection and try again.';
      case AppErrorType.server:
        return 'We\'re having trouble connecting to our servers. Please try again later.';
      case AppErrorType.notFound:
        return 'The content you\'re looking for doesn\'t exist or has been removed.';
      case AppErrorType.unauthorized:
        return 'You don\'t have permission to access this content.';
      case AppErrorType.generic:
        return 'An unexpected error occurred. Please try again.';
    }
  }
}

class AppErrorSnackBar {
  static void show(
    BuildContext context, {
    required String message,
    String? title,
    VoidCallback? onRetry,
    Duration duration = const Duration(seconds: 4),
  }) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Row(
          children: [
            const Icon(
              Icons.error_outline,
              color: AppColors.white,
              size: 20,
            ),
            const SizedBox(width: AppSpacing.sm),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (title != null)
                    Text(
                      title,
                      style: AppTypography.labelMedium.copyWith(
                        color: AppColors.white,
                      ),
                    ),
                  Text(
                    message,
                    style: AppTypography.bodySmall.copyWith(
                      color: AppColors.white,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        backgroundColor: AppColors.error,
        duration: duration,
        action: onRetry != null
            ? SnackBarAction(
                label: 'Retry',
                textColor: AppColors.white,
                onPressed: onRetry,
              )
            : null,
      ),
    );
  }
}

class AppSuccessSnackBar {
  static void show(
    BuildContext context, {
    required String message,
    String? title,
    Duration duration = const Duration(seconds: 3),
  }) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Row(
          children: [
            const Icon(
              Icons.check_circle_outline,
              color: AppColors.white,
              size: 20,
            ),
            const SizedBox(width: AppSpacing.sm),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (title != null)
                    Text(
                      title,
                      style: AppTypography.labelMedium.copyWith(
                        color: AppColors.white,
                      ),
                    ),
                  Text(
                    message,
                    style: AppTypography.bodySmall.copyWith(
                      color: AppColors.white,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        backgroundColor: AppColors.success,
        duration: duration,
      ),
    );
  }
}
