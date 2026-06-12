/// VoltVanguard Mobile Command Center — app entry point.
///
/// Startup sequence:
///   1. Ensure Flutter binding is initialised
///   2. Initialise [NotificationService] (registers Android channel, iOS perms)
///   3. Wrap app in [ProviderScope] (Riverpod DI root)
///   4. Hand routing to [appRouter] (go_router)
library;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/router/app_router.dart';
import 'core/services/notification_service.dart';
import 'core/theme/app_theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Lock to portrait (most EV dashboards are portrait on mobile)
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);

  // Status bar: dark icons on transparent bg (our dark theme handles the rest)
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor:            Colors.transparent,
      statusBarIconBrightness:   Brightness.light,
      systemNavigationBarColor:  AppTheme.surfaceNavy,
    ),
  );

  // Notification setup (creates Android channel, requests iOS permissions)
  await NotificationService().init();

  runApp(
    // ProviderScope is the root of the Riverpod dependency graph
    const ProviderScope(child: VoltVanguardApp()),
  );
}

class VoltVanguardApp extends ConsumerWidget {
  const VoltVanguardApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return MaterialApp.router(
      title:            'VoltVanguard',
      debugShowCheckedModeBanner: false,
      theme:            AppTheme.dark,
      routerConfig:     appRouter,
    );
  }
}
