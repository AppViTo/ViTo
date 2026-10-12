import 'package:flutter/material.dart';
import '../../features/splash/presentation/pages/splash_page.dart';
import '../../features/auth/presentation/pages/login_page.dart';
import '../../features/auth/presentation/pages/register_page.dart';
import '../../features/home/presentation/pages/main_wrapper_screen.dart';
import '../../features/events/presentation/pages/event_list_page.dart';
import '../../features/events/presentation/pages/create_event_screen.dart';
import '../../features/events/presentation/pages/event_detail_page.dart';
import '../../features/events/presentation/pages/qr_scanner_page.dart';
import '../../features/mosaic/presentation/pages/mosaic_page.dart';
import '../../features/streaming/presentation/pages/stream_host_page.dart';
import '../../features/streaming/presentation/pages/stream_viewer_page.dart';
import '../../features/chat/presentation/pages/chat_rooms_page.dart';
import '../../features/social/presentation/pages/profile_page.dart';

class AppRouter {
  AppRouter._();
  
  static const String initialRoute = '/';
  static const String login = '/login';
  static const String register = '/register';
  static const String main = '/main';
  static const String home = '/home';
  static const String events = '/events';
  static const String createEvent = '/create-event';
  static const String eventDetail = '/event-detail';
  static const String qrScanner = '/qr-scanner';
  static const String mosaic = '/mosaic';
  static const String streamHost = '/stream-host';
  static const String streamViewer = '/stream-viewer';
  static const String chat = '/chat';
  static const String profile = '/profile';
  
  static Route<dynamic> generateRoute(RouteSettings settings) {
    switch (settings.name) {
      case initialRoute:
        return MaterialPageRoute(builder: (_) => const SplashPage());
      case login:
        return MaterialPageRoute(builder: (_) => const LoginPage());
      case register:
        return MaterialPageRoute(builder: (_) => const RegisterPage());
      case home:
        return MaterialPageRoute(builder: (_) => const MainWrapperScreen());
      case main:
        return MaterialPageRoute(builder: (_) => const MainWrapperScreen());
      case events:
        return MaterialPageRoute(builder: (_) => const EventListPage());
      case createEvent:
        return MaterialPageRoute(builder: (_) => const CreateEventScreen());
      case eventDetail:
        final args = settings.arguments as Map<String, dynamic>?;
        return MaterialPageRoute(
          builder: (_) => EventDetailPage(eventId: args?['eventId'] ?? ''),
        );
      case qrScanner:
        final args = settings.arguments as Map<String, dynamic>?;
        return MaterialPageRoute(
          builder: (_) => QrScannerPage(eventId: args?['eventId'] ?? ''),
        );
      case mosaic:
        final args = settings.arguments as Map<String, dynamic>?;
        return MaterialPageRoute(
          builder: (_) => MosaicPage(eventId: args?['eventId'] ?? ''),
        );
      case streamHost:
        final args = settings.arguments as Map<String, dynamic>?;
        return MaterialPageRoute(
          builder: (_) => StreamHostPage(
            eventId: args?['eventId'] ?? '',
            eventTitle: args?['eventTitle'] ?? '',
          ),
        );
      case streamViewer:
        final args = settings.arguments as Map<String, dynamic>?;
        return MaterialPageRoute(
          builder: (_) => StreamViewerPage(streamId: args?['streamId'] ?? ''),
        );
      case chat:
        return MaterialPageRoute(builder: (_) => const ChatRoomsPage());
      case profile:
        return MaterialPageRoute(builder: (_) => const ProfilePage());
      default:
        return MaterialPageRoute(
          builder: (_) => Scaffold(
            body: Center(
              child: Text('No route defined for ${settings.name}'),
            ),
          ),
        );
    }
  }
}
