import 'package:flutter/material.dart';

class AppColors {
  // ── Brand Colors (ViTo Identity) ──
  static const Color grisCarbon = Color(0xFF0B0F19);
  static const Color coralNeon = Color(0xFFFF3366);
  static const Color lavandaPale = Color(0xFFD0BFFF);
  static const Color blancoHielo = Color(0xFFFFFFFF);

  // ── Primary ──
  static const Color primary = coralNeon;
  static const Color primaryLight = Color(0xFFFF6690);
  static const Color primaryDark = Color(0xFFCC2952);

  // ── Secondary ──
  static const Color secondary = lavandaPale;
  static const Color secondaryLight = Color(0xFFE0D4FF);
  static const Color secondaryDark = Color(0xFFB099E6);

  // ── Background & Surface ──
  static const Color backgroundLight = Color(0xFFF8F9FA);
  static const Color backgroundDark = grisCarbon;
  static const Color surfaceLight = blancoHielo;
  static const Color surfaceDark = Color(0xFF151A26);
  static const Color surfaceElevated = Color(0xFF161D2F);
  static const Color stepTrack = Color(0xFF222B38);

  // ── Text ──
  static const Color textPrimary = blancoHielo;
  static const Color textSecondary = Color(0xFF9CA3AF);
  static const Color textMuted = Color(0xFF8E9AAC);
  static const Color textHint = Color(0xFF6B7280);
  static const Color textOnDark = blancoHielo;
  static const Color textOnLight = grisCarbon;

  // ── Neutral ──
  static const Color white = blancoHielo;
  static const Color black = grisCarbon;
  static const Color grey50 = Color(0xFFFAFAFA);
  static const Color grey100 = Color(0xFFF5F5F5);
  static const Color grey200 = Color(0xFFEEEEEE);
  static const Color grey300 = Color(0xFFE0E0E0);
  static const Color grey400 = Color(0xFFBDBDBD);
  static const Color grey500 = Color(0xFF9E9E9E);
  static const Color grey600 = Color(0xFF757575);
  static const Color grey700 = Color(0xFF616161);
  static const Color grey800 = Color(0xFF424242);
  static const Color grey900 = Color(0xFF212121);

  // ── Semantic ──
  static const Color success = Color(0xFF10B981);
  static const Color successLight = Color(0xFF34D399);
  static const Color warning = Color(0xFFF59E0B);
  static const Color warningLight = Color(0xFFFBBF24);
  static const Color error = coralNeon;
  static const Color errorLight = Color(0xFFFF6690);
  static const Color info = lavandaPale;

  // ── Status ──
  static const Color online = Color(0xFF10B981);
  static const Color offline = Color(0xFF6B7280);
  static const Color busy = warning;

  // ── Event Colors ──
  static const Color eventBlue = Color(0xFF3B82F6);
  static const Color eventGreen = Color(0xFF10B981);
  static const Color eventPurple = lavandaPale;
  static const Color eventPink = coralNeon;
  static const Color eventOrange = Color(0xFFF97316);

  // ── Gradients ──
  static const LinearGradient primaryGradient = LinearGradient(
    colors: [coralNeon, Color(0xFFFF6690)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const LinearGradient darkGradient = LinearGradient(
    colors: [grisCarbon, Color(0xFF151A26)],
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
  );

  static const LinearGradient accentGradient = LinearGradient(
    colors: [lavandaPale, Color(0xFFE0D4FF)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const LinearGradient splashGradient = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [grisCarbon, grisCarbon],
  );
}
