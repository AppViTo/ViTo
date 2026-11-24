import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../core/di/injection_container.dart';
import '../../features/events/presentation/bloc/event_bloc.dart';
import '../../features/events/presentation/bloc/invite_link_cubit.dart';
import '../../features/events/presentation/bloc/invite_links_cubit.dart';
import '../../features/events/presentation/pages/invite_link_page.dart';
import '../../features/events/presentation/pages/invite_links_page.dart';
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
import '../../features/archive/presentation/pages/archive_detail_page.dart';

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
  static const String archiveDetail = '/archive-detail';
  static const String inviteLinks = '/invite-links';

  /// Prefijo de los enlaces de invitación: `/i/<token>` (T043).
  static const String inviteLinkPrefix = '/i/';

  /// Token de una ruta `/i/<token>`, o `null` si la ruta es otra.
  static String? inviteTokenOf(String? routeName) {
    if (routeName == null || !routeName.startsWith(inviteLinkPrefix)) {
      return null;
    }
    final token = Uri.parse(routeName).pathSegments.skip(1).firstOrNull;
    return token == null || token.isEmpty ? null : token;
  }

  /// Rutas al abrir la app (o al recargar la web). Siempre empieza en el
  /// splash, que restaura la sesión y decide entre inicio y login: abrir
  /// `/home` o `/event-detail` directo se saltaría la sesión. La única
  /// excepción es `/i/<token>`, que muestra la invitación sin sesión (T043).
  ///
  /// En web, Flutter pasa aquí la ruta de la URL aunque `initialRoute` diga
  /// otra cosa, así que la decisión tiene que estar en esta función.
  static List<Route<dynamic>> generateInitialRoutes(String initialRoute) {
    final inviteRoute = inviteTokenOf(initialRoute) != null ? initialRoute : null;
    return [generateRoute(RouteSettings(name: inviteRoute ?? AppRouter.initialRoute))];
  }

  static Route<dynamic> generateRoute(RouteSettings settings) {
    final inviteToken = inviteTokenOf(settings.name);
    if (inviteToken != null) {
      return MaterialPageRoute(
        settings: settings,
        builder: (_) => BlocProvider(
          create: (_) => InviteLinkCubit(
            token: inviteToken,
            getPreview: sl(),
            acceptInviteLink: sl(),
          ),
          child: InviteLinkPage(token: inviteToken),
        ),
      );
    }

    switch (settings.name) {
      case inviteLinks:
        final args = settings.arguments as Map<String, dynamic>?;
        final eventId = args?['eventId'] as String? ?? '';
        return MaterialPageRoute(
          builder: (_) => BlocProvider(
            create: (_) => InviteLinksCubit(
              eventId: eventId,
              listInviteLinks: sl(),
              createInviteLink: sl(),
              revokeInviteLink: sl(),
            ),
            child: InviteLinksPage(
              eventTitle: args?['eventTitle'] as String? ?? '',
            ),
          ),
        );
      case initialRoute:
        return MaterialPageRoute(
          settings: settings,
          builder: (_) => const SplashPage(),
        );
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
        // Un EventBloc por detalle: los estados de otras pantallas (escáner,
        // crear evento, otro detalle) no llegan a este.
        return MaterialPageRoute(
          builder: (_) => BlocProvider<EventBloc>(
            create: (_) => sl<EventBloc>(),
            child: EventDetailPage(eventId: args?['eventId'] ?? ''),
          ),
        );
      case qrScanner:
        final args = settings.arguments as Map<String, dynamic>?;
        return MaterialPageRoute(
          builder: (_) => QrScannerPage(eventId: args?['eventId'] ?? ''),
        );
      case mosaic:
        final args = settings.arguments as Map<String, dynamic>?;
        return MaterialPageRoute(
          builder: (_) => MosaicPage(
            eventId: args?['eventId'] ?? '',
            organizerId: args?['organizerId'] as String?,
          ),
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
      case archiveDetail:
        final args = settings.arguments as Map<String, dynamic>?;
        return MaterialPageRoute(
          builder: (_) => ArchiveDetailPage(archiveId: args?['archiveId'] ?? ''),
        );
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
