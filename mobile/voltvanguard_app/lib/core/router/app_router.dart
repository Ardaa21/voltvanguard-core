/// go_router configuration for VoltVanguard.
///
/// Route tree:
///   /                     → DashboardScreen (fleet overview)
///   /vehicle/:id          → VehicleDetailScreen (live telemetry)
///   /reservations         → ReservationsScreen (active AI tasks)
///   /reservations/:taskId → ReservationDetailScreen
library;

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../features/dashboard/presentation/screens/dashboard_screen.dart';
import '../../features/reservations/presentation/screens/reservations_screen.dart';
import '../../features/vehicle/presentation/screens/vehicle_detail_screen.dart';

// ── Route name constants ─────────────────────────────────────────────────────

abstract final class AppRoutes {
  static const String dashboard           = '/';
  static const String vehicle             = '/vehicle/:id';
  static const String reservations        = '/reservations';
  static const String reservationDetail   = '/reservations/:taskId';

  static String vehiclePath(String id)         => '/vehicle/$id';
  static String reservationDetailPath(String t) => '/reservations/$t';
}

// ── Router ────────────────────────────────────────────────────────────────────

final GoRouter appRouter = GoRouter(
  debugLogDiagnostics: true,
  initialLocation: AppRoutes.dashboard,
  routes: [
    // ── Shell with bottom nav ──────────────────────────────────────────────
    StatefulShellRoute.indexedStack(
      builder: (context, state, navigationShell) =>
          _ShellScaffold(navigationShell: navigationShell),
      branches: [
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: AppRoutes.dashboard,
              name: 'dashboard',
              builder: (context, state) => const DashboardScreen(),
            ),
          ],
        ),
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: AppRoutes.reservations,
              name: 'reservations',
              builder: (context, state) => const ReservationsScreen(),
            ),
          ],
        ),
      ],
    ),

    // ── Full-screen vehicle detail (no bottom nav) ─────────────────────────
    GoRoute(
      path: AppRoutes.vehicle,
      name: 'vehicle_detail',
      builder: (context, state) => VehicleDetailScreen(
        vehicleId: state.pathParameters['id']!,
      ),
    ),
  ],

  errorBuilder: (context, state) => Scaffold(
    body: Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.error_outline, size: 64, color: Colors.red),
          const SizedBox(height: 16),
          Text('Page not found: ${state.uri}'),
        ],
      ),
    ),
  ),
);

// ── Shell scaffold with bottom navigation ─────────────────────────────────────

class _ShellScaffold extends StatelessWidget {
  const _ShellScaffold({required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: NavigationBar(
        selectedIndex: navigationShell.currentIndex,
        onDestinationSelected: navigationShell.goBranch,
        destinations: const [
          NavigationDestination(
            icon:           Icon(Icons.dashboard_outlined),
            selectedIcon:   Icon(Icons.dashboard),
            label:          'Fleet',
          ),
          NavigationDestination(
            icon:           Icon(Icons.bolt_outlined),
            selectedIcon:   Icon(Icons.bolt),
            label:          'Reservations',
          ),
        ],
      ),
    );
  }
}
