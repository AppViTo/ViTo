import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../auth/presentation/bloc/auth_bloc.dart';
import '../../../events/presentation/pages/event_list_page.dart';
import '../../../chat/presentation/pages/chat_rooms_page.dart';
import '../../../mosaic/presentation/pages/mosaic_page.dart';
import '../../../social/presentation/pages/profile_page.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  int _currentIndex = 0;

  final List<Widget> _pages = const [
    EventListPage(),
    ChatRoomsPage(),
    MosaicPage(eventId: ''),
    ProfilePage(),
  ];

  @override
  Widget build(BuildContext context) {
    return BlocListener<AuthBloc, AuthState>(
      listener: (context, state) {
        if (state is AuthUnauthenticated) {
          Navigator.pushReplacementNamed(context, '/login');
        }
      },
      child: Scaffold(
        backgroundColor: AppColors.grisCarbon,
        body: IndexedStack(
          index: _currentIndex,
          children: _pages,
        ),
        bottomNavigationBar: Container(
          decoration: BoxDecoration(
            color: AppColors.grisCarbon,
            border: Border(
              top: BorderSide(
                color: AppColors.grey800.withValues(alpha: 0.5),
                width: 0.5,
              ),
            ),
          ),
          child: NavigationBar(
            selectedIndex: _currentIndex,
            onDestinationSelected: (index) {
              setState(() => _currentIndex = index);
            },
            backgroundColor: Colors.transparent,
            elevation: 0,
            height: 70,
            labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
            indicatorColor: AppColors.coralNeon.withValues(alpha: 0.12),
            destinations: const [
              NavigationDestination(
                icon: Icon(Icons.event_outlined, size: 24),
                selectedIcon: Icon(Icons.event_rounded, size: 24, color: AppColors.coralNeon),
                label: 'Eventos',
              ),
              NavigationDestination(
                icon: Icon(Icons.chat_bubble_outline, size: 24),
                selectedIcon: Icon(Icons.chat_bubble_rounded, size: 24, color: AppColors.coralNeon),
                label: 'Chat',
              ),
              NavigationDestination(
                icon: Icon(Icons.grid_view_outlined, size: 24),
                selectedIcon: Icon(Icons.grid_view_rounded, size: 24, color: AppColors.coralNeon),
                label: 'Mosaico',
              ),
              NavigationDestination(
                icon: Icon(Icons.person_outline, size: 24),
                selectedIcon: Icon(Icons.person_rounded, size: 24, color: AppColors.coralNeon),
                label: 'Perfil',
              ),
            ],
          ),
        ),
      ),
    );
  }
}
