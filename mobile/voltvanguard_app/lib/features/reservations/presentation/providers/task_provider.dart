/// Riverpod providers for the reservations (autonomous tasks) feature.
///
/// [activeTasksProvider] polls the backend every 10 s.
/// When a new CHARGE_SCHEDULING task appears that wasn't in the previous
/// snapshot, [taskNotificationWatcherProvider] fires a local notification.
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_client.dart';
import '../../../../core/services/notification_service.dart';
import '../../../dashboard/presentation/providers/telemetry_provider.dart';
import '../../data/models/task_model.dart';
import '../../data/repositories/task_repository_impl.dart';

// ── Repository ─────────────────────────────────────────────────────────────────

final taskRepositoryProvider = Provider<TaskRepository>(
  (ref) => TaskRepositoryImpl(apiClient: ref.watch(apiClientProvider)),
  name: 'taskRepositoryProvider',
);

// ── Active tasks — polling stream ─────────────────────────────────────────────

final activeTasksProvider = StreamProvider<List<TaskModel>>(
  (ref) =>
      ref.watch(taskRepositoryProvider).watchActiveTasks(
        interval: const Duration(seconds: 10),
      ),
  name: 'activeTasksProvider',
);

// ── Single task detail ─────────────────────────────────────────────────────────

final taskDetailProvider = FutureProvider.family<TaskModel, String>(
  (ref, taskId) => ref.watch(taskRepositoryProvider).getTaskById(taskId),
  name: 'taskDetailProvider',
);

// ── Notification watcher ──────────────────────────────────────────────────────
///
/// Watches [activeTasksProvider] for tasks that are newly created
/// (CHARGE_SCHEDULING, PENDING, arrived in latest snapshot).
/// Fires a local notification + iOS Live Activity for each new one.

final taskNotificationWatcherProvider = Provider<void>(
  (ref) {
    // Scoped inside the provider so it resets if the provider is disposed.
    final seenTaskIds = <String>{};

    ref.listen<AsyncValue<List<TaskModel>>>(
      activeTasksProvider,
      (previous, next) async {
        if (!next.hasValue) return;
        final tasks = next.value!;
        final currentIds = tasks.map((t) => t.id).toSet();

        for (final task in tasks) {
          if (seenTaskIds.contains(task.id)) continue;
          seenTaskIds.add(task.id);

          // Only notify on brand-new CHARGE_SCHEDULING tasks
          if (task.taskType != TaskType.chargeScheduling) continue;

          final payload = task.reservationPayload;
          if (payload == null) continue;

          await NotificationService().showReservationNotification(
            ReservationNotificationPayload(
              taskId:           task.id,
              vehicleId:        task.vehicleId,
              stationName:      payload.stationName,
              urgency:          payload.urgency,
              batteryPct:       payload.vehicleBatteryPercent,
              estimatedRangeKm: payload.estimatedRangeKm,
            ),
          );
        }

        // Prune IDs for tasks no longer in the active list to prevent unbounded growth.
        // Use removeWhere instead of removeAll so we only drop IDs not currently active.
        if (seenTaskIds.length > 500) {
          seenTaskIds.removeWhere((id) => !currentIds.contains(id));
        }
      },
    );
  },
  name: 'taskNotificationWatcherProvider',
);
