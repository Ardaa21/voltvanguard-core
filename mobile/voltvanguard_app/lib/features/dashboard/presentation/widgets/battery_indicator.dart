/// Animated circular battery gauge + linear strip variant.
library;

import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../../../../core/theme/app_theme.dart';

// ── Circular gauge ─────────────────────────────────────────────────────────────

class BatteryGauge extends StatelessWidget {
  const BatteryGauge({
    super.key,
    required this.percent,
    this.size = 80,
    this.strokeWidth = 8,
    this.showLabel = true,
    this.animate = true,
  });

  final double  percent;      // 0.0 – 100.0
  final double  size;
  final double  strokeWidth;
  final bool    showLabel;
  final bool    animate;

  @override
  Widget build(BuildContext context) {
    final color  = AppTheme.batteryColor(percent);
    final isCrit = percent <= 15;

    Widget gauge = SizedBox(
      width:  size,
      height: size,
      child: Stack(
        alignment: Alignment.center,
        children: [
          // ── Arc ────────────────────────────────────────────────────────
          CustomPaint(
            size: Size(size, size),
            painter: _ArcPainter(
              percent:     percent,
              color:       color,
              strokeWidth: strokeWidth,
            ),
          ),

          // ── Centre label ───────────────────────────────────────────────
          if (showLabel)
            Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  '${percent.toStringAsFixed(0)}%',
                  style: TextStyle(
                    fontFamily:  'Inter',
                    fontWeight:  FontWeight.w700,
                    fontSize:    size * 0.22,
                    color:       color,
                    height:      1.1,
                  ),
                ),
                Text(
                  'BAT',
                  style: TextStyle(
                    fontFamily: 'Inter',
                    fontSize:   size * 0.12,
                    color:      AppTheme.mutedBlue,
                    letterSpacing: 1,
                  ),
                ),
              ],
            ),
        ],
      ),
    );

    if (animate && isCrit) {
      // Pulse on critical
      gauge = gauge
          .animate(onPlay: (c) => c.repeat(reverse: true))
          .scaleXY(end: 1.06, duration: 800.ms, curve: Curves.easeInOut);
    }

    return gauge;
  }
}

class _ArcPainter extends CustomPainter {
  const _ArcPainter({
    required this.percent,
    required this.color,
    required this.strokeWidth,
  });

  final double percent;
  final Color  color;
  final double strokeWidth;

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = (size.width - strokeWidth) / 2;

    // Background track
    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      -math.pi * 1.25,
      math.pi * 2.5,
      false,
      Paint()
        ..color       = AppTheme.cardNavy
        ..strokeWidth = strokeWidth
        ..style       = PaintingStyle.stroke
        ..strokeCap   = StrokeCap.round,
    );

    // Filled arc
    final sweep = (percent / 100).clamp(0.0, 1.0) * math.pi * 2.5;
    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      -math.pi * 1.25,
      sweep,
      false,
      Paint()
        ..color        = color
        ..strokeWidth  = strokeWidth
        ..style        = PaintingStyle.stroke
        ..strokeCap    = StrokeCap.round
        ..shader       = LinearGradient(
          colors: [color.withOpacity(0.6), color],
        ).createShader(
          Rect.fromCircle(center: center, radius: radius),
        ),
    );
  }

  @override
  bool shouldRepaint(_ArcPainter old) =>
      old.percent != percent || old.color != color;
}

// ── Linear strip ───────────────────────────────────────────────────────────────

class BatteryStrip extends StatelessWidget {
  const BatteryStrip({
    super.key,
    required this.percent,
    this.height = 6,
    this.borderRadius = 4,
    this.animate = true,
  });

  final double percent;
  final double height;
  final double borderRadius;
  final bool   animate;

  @override
  Widget build(BuildContext context) {
    final color = AppTheme.batteryColor(percent);
    final frac  = (percent / 100).clamp(0.0, 1.0);

    return LayoutBuilder(
      builder: (context, constraints) {
        final barWidth = constraints.maxWidth * frac;
        return Container(
          height:       height,
          decoration:   BoxDecoration(
            color:        AppTheme.cardNavy,
            borderRadius: BorderRadius.circular(borderRadius),
          ),
          child: Align(
            alignment: Alignment.centerLeft,
            child: AnimatedContainer(
              duration:     animate ? 600.ms : Duration.zero,
              curve:        Curves.easeOut,
              width:        barWidth,
              height:       height,
              decoration:   BoxDecoration(
                borderRadius: BorderRadius.circular(borderRadius),
                gradient: LinearGradient(
                  colors: [color.withOpacity(0.7), color],
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}
