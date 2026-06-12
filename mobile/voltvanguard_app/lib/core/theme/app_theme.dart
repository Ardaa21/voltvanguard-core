import 'package:flutter/material.dart';

/// VoltVanguard design system.
///
/// Palette is inspired by EV dashboards: deep navy backgrounds,
/// electric-teal accents, amber warnings, crimson alerts.
abstract final class AppTheme {
  // ── Brand colours ──────────────────────────────────────────────────────────
  static const Color electricTeal   = Color(0xFF00D4AA);
  static const Color deepNavy       = Color(0xFF0A1628);
  static const Color surfaceNavy    = Color(0xFF0F2039);
  static const Color cardNavy       = Color(0xFF162B44);
  static const Color amber          = Color(0xFFFFB300);
  static const Color crimson        = Color(0xFFEF4444);
  static const Color softWhite      = Color(0xFFF0F4FF);
  static const Color mutedBlue      = Color(0xFF8BA3BF);

  // ── Battery level colours ──────────────────────────────────────────────────
  static const Color batteryHigh    = Color(0xFF22C55E); // > 50%
  static const Color batteryMedium  = Color(0xFFFFB300); // 25–50%
  static const Color batteryLow     = Color(0xFFFF6B35); // 15–25%
  static const Color batteryCritical = Color(0xFFEF4444); // < 15%

  // ── Status colours ──────────────────────────────────────────────────────────
  static const Color statusOnline   = Color(0xFF22C55E);
  static const Color statusOffline  = Color(0xFF64748B);
  static const Color statusCharging = Color(0xFF00D4AA);
  static const Color statusCritical = Color(0xFFEF4444);

  // ── Typography ────────────────────────────────────────────────────────────
  static const TextTheme _textTheme = TextTheme(
    displayLarge:  TextStyle(fontFamily: 'Inter', fontWeight: FontWeight.w700, fontSize: 32, letterSpacing: -0.5, color: softWhite),
    displayMedium: TextStyle(fontFamily: 'Inter', fontWeight: FontWeight.w700, fontSize: 28, letterSpacing: -0.3, color: softWhite),
    titleLarge:    TextStyle(fontFamily: 'Inter', fontWeight: FontWeight.w600, fontSize: 20, color: softWhite),
    titleMedium:   TextStyle(fontFamily: 'Inter', fontWeight: FontWeight.w600, fontSize: 16, color: softWhite),
    bodyLarge:     TextStyle(fontFamily: 'Inter', fontWeight: FontWeight.w400, fontSize: 16, color: softWhite),
    bodyMedium:    TextStyle(fontFamily: 'Inter', fontWeight: FontWeight.w400, fontSize: 14, color: mutedBlue),
    bodySmall:     TextStyle(fontFamily: 'Inter', fontWeight: FontWeight.w400, fontSize: 12, color: mutedBlue),
    labelLarge:    TextStyle(fontFamily: 'Inter', fontWeight: FontWeight.w600, fontSize: 14, letterSpacing: 0.5),
  );

  // ── Dark theme (primary) ───────────────────────────────────────────────────
  static final ThemeData dark = ThemeData(
    useMaterial3: true,
    brightness: Brightness.dark,
    fontFamily: 'Inter',
    colorScheme: const ColorScheme.dark(
      primary:          electricTeal,
      onPrimary:        deepNavy,
      secondary:        amber,
      onSecondary:      deepNavy,
      error:            crimson,
      surface:          surfaceNavy,
      onSurface:        softWhite,
      outline:          cardNavy,
      surfaceContainerHighest: cardNavy,
    ),
    scaffoldBackgroundColor: deepNavy,
    textTheme: _textTheme,
    appBarTheme: const AppBarTheme(
      backgroundColor:  deepNavy,
      foregroundColor:  softWhite,
      elevation:        0,
      centerTitle:      false,
      titleTextStyle:   TextStyle(
        fontFamily:   'Inter',
        fontWeight:   FontWeight.w600,
        fontSize:     20,
        color:        softWhite,
      ),
    ),
    cardTheme: CardThemeData(
      color:            cardNavy,
      elevation:        0,
      shape:            RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: Color(0xFF1E3A5A), width: 1),
      ),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: electricTeal,
        foregroundColor: deepNavy,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        textStyle: const TextStyle(fontFamily: 'Inter', fontWeight: FontWeight.w600, fontSize: 15),
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
      ),
    ),
    dividerTheme: const DividerThemeData(
      color:     Color(0xFF1E3A5A),
      thickness: 1,
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor:     surfaceNavy,
      indicatorColor:      electricTeal.withOpacity(0.15),
      iconTheme:           const WidgetStatePropertyAll(
        IconThemeData(color: mutedBlue, size: 24),
      ),
      labelTextStyle:      const WidgetStatePropertyAll(
        TextStyle(fontFamily: 'Inter', fontSize: 12, fontWeight: FontWeight.w500, color: mutedBlue),
      ),
    ),
    chipTheme: ChipThemeData(
      backgroundColor:     cardNavy,
      labelStyle:          const TextStyle(fontFamily: 'Inter', fontSize: 12, color: softWhite),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      side: const BorderSide(color: Color(0xFF1E3A5A)),
    ),
  );

  // ── Helpers ────────────────────────────────────────────────────────────────

  /// Returns the correct colour for a given battery percentage.
  static Color batteryColor(double percent) {
    if (percent <= 15) return batteryCritical;
    if (percent <= 25) return batteryLow;
    if (percent <= 50) return batteryMedium;
    return batteryHigh;
  }
}
