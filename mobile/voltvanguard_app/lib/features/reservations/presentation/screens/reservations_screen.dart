/// Active autonomous task list — polls every 10 s, triggers notifications
/// when new CHARGE_SCHEDULING tasks arrive.
library;

import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shimmer/shimmer.dart';

import '../../../../core/theme/app_theme.dart';
import '../../data/models/task_model.dart';
import '../providers/task_provider.dart';

class ReservationsScreen extends ConsumerWidget {
  const ReservationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Activate notification watcher
    ref.watch(taskNotificationWatcherProvider);

    final tasksAsync = ref.watch(activeTasksProvider);

    return Scaffold(
      backgroundColor: AppTheme.deepNavy,
      appBar: AppBar(
        backgroundColor: AppTheme.deepNavy,
        title: const Row(
          children: [
            Icon(Icons.bolt, color: AppTheme.electricTeal, size: 22),
            SizedBox(width: 8),
            Text('AI Reservations'),
          ],
        ),
        actions: [
          // ── Auto-refresh indicator ──────────────────────────────────────
          Padding(
            padding: const EdgeInsets.only(right: 16),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 6, height: 6,
                  decoration: const BoxDecoration(
                    color: AppTheme.amber, shape: BoxShape.circle,
                  ),
                )
                    .animate(onPlay: (c) => c.repeat(reverse: true))
                    .scaleXY(end: 1.6, duration: 800.ms),
                const SizedBox(width: 4),
                const Text(
                  'POLLING 10s',
                  style: TextStyle(
                    fontFamily:   'Inter',
                    fontSize:     10,
                    fontWeight:   FontWeight.w600,
                    color:        AppTheme.mutedBlue,
                    letterSpacing: 0.8,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
      body: tasksAsync.when(
        loading: () => _LoadingList(),
        error:   (e, _) => _ErrorView(error: e.toString()),
        data:    (tasks) => _TaskList(tasks: tasks),
      ),
    );
  }
}

// ── Task list ──────────────────────────────────────────────────────────────────

class _TaskList extends StatelessWidget {
  const _TaskList({required this.tasks});
  final List<TaskModel> tasks;

  @override
  Widget build(BuildContext context) {
    if (tasks.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.check_circle_outline,
                size: 64, color: AppTheme.statusOnline),
            const SizedBox(height: 16),
            Text(
              'No active tasks',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            const Text(
              'The AI agent has no pending reservations.\nAll vehicles are within safe battery range.',
              textAlign: TextAlign.center,
              style: TextStyle(color: AppTheme.mutedBlue, fontSize: 13),
            ),
          ],
        ),
      ).animate().fadeIn(duration: 300.ms);
    }

    return ListView.separated(
      padding:     const EdgeInsets.all(16),
      itemCount:   tasks.length,
      separatorBuilder: (_, __) => const SizedBox(height: 10),
      itemBuilder: (context, index) => _TaskCard(
            // ValueKey tracks tasks by ID so animations don't misfire when
            // the list is refreshed and tasks are re-ordered.
            key:  ValueKey(tasks[index].id),
            task: tasks[index],
          )
          .animate(delay: (index * 60).ms)
          .fadeIn(duration: 350.ms)
          .slideY(begin: 0.05, end: 0),
    );
  }
}

// ── Task card ──────────────────────────────────────────────────────────────────

class _TaskCard extends StatelessWidget {
  const _TaskCard({super.key, required this.task});
  final TaskModel task;

  @override
  Widget build(BuildContext context) {
    final reservation = task.reservationPayload;
    final urgency     = reservation?.urgency ?? 'medium';
    final accentColor = _urgencyColor(urgency);

    return Container(
      decoration: BoxDecoration(
        color:        AppTheme.cardNavy,
        borderRadius: BorderRadius.circular(14),
        border: Border(
          left: BorderSide(color: accentColor, width: 3),
          top:  const BorderSide(color: Color(0xFF1E3A5A)),
          right:const BorderSide(color: Color(0xFF1E3A5A)),
          bottom:const BorderSide(color: Color(0xFF1E3A5A)),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // ── Header ──────────────────────────────────────────────────
            Row(
              children: [
                _UrgencyBadge(urgency: urgency, color: accentColor),
                const Spacer(),
                _StatusBadge(status: task.status),
                const SizedBox(width: 8),
                _PriorityChip(label: task.priorityLabel),
              ],
            ),
            const SizedBox(height: 10),

            // ── Station name ─────────────────────────────────────────────
            if (reservation != null) ...[
              Row(
                children: [
                  const Icon(Icons.ev_station_outlined, size: 16, color: AppTheme.electricTeal),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      reservation.stationName,
                      style: const TextStyle(
                        fontFamily: 'Inter', fontSize: 14,
                        fontWeight: FontWeight.w600, color: AppTheme.softWhite,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 6),

              // ── Battery context ──────────────────────────────────────
              Row(
                children: [
                  const Icon(Icons.battery_alert_rounded, size: 14, color: AppTheme.mutedBlue),
                  const SizedBox(width: 4),
                  Text(
                    'Battery: ${reservation.vehicleBatteryPercent.toStringAsFixed(0)}%  →  '
                    'Charge to: ${reservation.requestedChargeToPercent}%',
                    style: const TextStyle(
                      fontFamily: 'Inter', fontSize: 12, color: AppTheme.mutedBlue,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 6),

              // ── AI reasoning ─────────────────────────────────────────
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color:        AppTheme.deepNavy,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.smart_toy_outlined, size: 13, color: AppTheme.electricTeal),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        reservation.reasoning,
                        style: const TextStyle(
                          fontFamily: 'Inter', fontSize: 11, color: AppTheme.mutedBlue, height: 1.4,
                        ),
                        maxLines: 3,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
            ] else ...[
              // Fallback for non-CHARGE_SCHEDULING tasks
              Text(
                task.taskType.displayName,
                style: const TextStyle(
                  fontFamily: 'Inter', fontSize: 14,
                  fontWeight: FontWeight.w600, color: AppTheme.softWhite,
                ),
              ),
            ],

            const SizedBox(height: 10),

            // ── Footer: vehicle ID + created at ─────────────────────────
            Row(
              children: [
                const Icon(Icons.electric_car_outlined, size: 12, color: AppTheme.mutedBlue),
                const SizedBox(width: 4),
                Text(
                  task.vehicleId.substring(0, 8),
                  style: const TextStyle(fontFamily: 'Inter', fontSize: 11, color: AppTheme.mutedBlue),
                ),
                const Spacer(),
                if (task.createdAt != null)
                  Text(
                    _formatTime(task.createdAt!),
                    style: const TextStyle(fontFamily: 'Inter', fontSize: 11, color: AppTheme.mutedBlue),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Color _urgencyColor(String urgency) => switch (urgency.toLowerCase()) {
    'critical' => AppTheme.crimson,
    'high'     => AppTheme.amber,
    'medium'   => AppTheme.electricTeal,
    _          => AppTheme.mutedBlue,
  };

  String _formatTime(DateTime dt) {
    final now  = DateTime.now();
    final diff = now.difference(dt);
    if (diff.inSeconds < 60)  return '${diff.inSeconds}s ago';
    if (diff.inMinutes < 60)  return '${diff.inMinutes}m ago';
    return '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  }
}

// ── Badge widgets ──────────────────────────────────────────────────────────────

class _UrgencyBadge extends StatelessWidget {
  const _UrgencyBadge({required this.urgency, required this.color});
  final String urgency;
  final Color  color;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
    decoration: BoxDecoration(
      color:        color.withOpacity(0.12),
      borderRadius: BorderRadius.circular(6),
      border:       Border.all(color: color.withOpacity(0.3)),
    ),
    child: Text(
      urgency.toUpperCase(),
      style: TextStyle(
        fontFamily:   'Inter',
        fontSize:     10,
        fontWeight:   FontWeight.w700,
        color:        color,
        letterSpacing: 1,
      ),
    ),
  );
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.status});
  final TaskStatus status;

  @override
  Widget build(BuildContext context) {
    final (color, label) = switch (status) {
      TaskStatus.pending    => (AppTheme.amber,          'PENDING'),
      TaskStatus.inProgress => (AppTheme.electricTeal,   'IN PROGRESS'),
      TaskStatus.completed  => (AppTheme.statusOnline,   'DONE'),
      TaskStatus.failed     => (AppTheme.crimson,        'FAILED'),
      _                     => (AppTheme.mutedBlue,      status.name.toUpperCase()),
    };
    return Text(label,
      style: TextStyle(
        fontFamily:   'Inter',
        fontSize:     10,
        fontWeight:   FontWeight.w600,
        color:        color,
        letterSpacing: 0.5,
      ),
    );
  }
}

class _PriorityChip extends StatelessWidget {
  const _PriorityChip({required this.label});
  final String label;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
    decoration: BoxDecoration(
      color:        AppTheme.deepNavy,
      borderRadius: BorderRadius.circular(4),
    ),
    child: Text(
      'P: $label',
      style: const TextStyle(
        fontFamily: 'Inter', fontSize: 10, color: AppTheme.mutedBlue,
      ),
    ),
  );
}

// ── Loading / Error ───────────────────────────────────────────────────────────

class _LoadingList extends StatelessWidget {
  @override
  Widget build(BuildContext context) => ListView.separated(
    padding:     const EdgeInsets.all(16),
    itemCount:   4,
    separatorBuilder: (_, __) => const SizedBox(height: 10),
    itemBuilder: (_, __) => Shimmer.fromColors(
      baseColor:      AppTheme.cardNavy,
      highlightColor: AppTheme.surfaceNavy,
      child: Container(
        height: 120,
        decoration: BoxDecoration(
          color:        AppTheme.cardNavy,
          borderRadius: BorderRadius.circular(14),
        ),
      ),
    ),
  );
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.error});
  final String error;

  @override
  Widget build(BuildContext context) => Center(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(Icons.error_outline, size: 48, color: AppTheme.crimson),
        const SizedBox(height: 12),
        const Text('Could not load tasks', style: TextStyle(color: AppTheme.softWhite)),
        const SizedBox(height: 6),
        Text(error,
          style: const TextStyle(fontSize: 11, color: AppTheme.mutedBlue),
          textAlign: TextAlign.center,
        ),
      ],
    ),
  );
}
