import 'package:flutter/material.dart';
import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';

enum AppCardType { elevation, outlined, filled }

class AppCard extends StatelessWidget {
  final Widget child;
  final AppCardType type;
  final EdgeInsetsGeometry? padding;
  final EdgeInsetsGeometry? margin;
  final double? width;
  final double? height;
  final Color? backgroundColor;
  final Color? borderColor;
  final double borderRadius;
  final VoidCallback? onTap;
  final bool isSelectable;
  final bool isSelected;
  
  const AppCard({
    super.key,
    required this.child,
    this.type = AppCardType.elevation,
    this.padding,
    this.margin,
    this.width,
    this.height,
    this.backgroundColor,
    this.borderColor,
    this.borderRadius = AppSpacing.radiusMd,
    this.onTap,
    this.isSelectable = false,
    this.isSelected = false,
  });
  
  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        width: width,
        height: height,
        margin: margin ?? const EdgeInsets.all(AppSpacing.sm),
        padding: padding ?? const EdgeInsets.all(AppSpacing.lg),
        decoration: _getDecoration(),
        child: child,
      ),
    );
  }
  
  BoxDecoration _getDecoration() {
    Color bgColor;
    Color? bColor;
    List<BoxShadow>? boxShadow;
    
    switch (type) {
      case AppCardType.elevation:
        bgColor = backgroundColor ?? AppColors.white;
        boxShadow = [
          BoxShadow(
            color: AppColors.black.withOpacity(0.05),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ];
        break;
        
      case AppCardType.outlined:
        bgColor = backgroundColor ?? AppColors.white;
        bColor = borderColor ?? AppColors.grey200;
        break;
        
      case AppCardType.filled:
        bgColor = backgroundColor ?? AppColors.grey50;
        break;
    }
    
    if (isSelectable && isSelected) {
      bColor = AppColors.primary;
      boxShadow = [
        BoxShadow(
          color: AppColors.primary.withOpacity(0.2),
          blurRadius: 8,
          offset: const Offset(0, 2),
        ),
      ];
    }
    
    return BoxDecoration(
      color: bgColor,
      borderRadius: BorderRadius.circular(borderRadius),
      border: bColor != null
          ? Border.all(
              color: bColor,
              width: isSelectable && isSelected ? 2 : 1,
            )
          : null,
      boxShadow: boxShadow,
    );
  }
}
