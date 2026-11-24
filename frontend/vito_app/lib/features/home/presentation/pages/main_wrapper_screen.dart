import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../auth/presentation/bloc/auth_bloc.dart';
import '../../../social/presentation/pages/profile_page.dart';
import '../widgets/custom_bottom_nav_bar.dart';
import 'home_screen.dart';
import 'map_screen.dart';
import 'people_screen.dart';

class MainWrapperScreen extends StatefulWidget {
  const MainWrapperScreen({super.key});

  @override
  State<MainWrapperScreen> createState() => _MainWrapperScreenState();
}

class _MainWrapperScreenState extends State<MainWrapperScreen> {
  static const _profileTab = 4;

  int _currentIndex = 0;

  /// Sube cada vez que se vuelve a la pestaña Perfil, para recargarla: el
  /// `IndexedStack` la mantiene viva con los datos de la primera carga.
  final _profileShown = ValueNotifier<int>(0);

  late final List<Widget> _pages;

  @override
  void initState() {
    super.initState();
    _pages = [
      const HomeScreen(),
      const MapScreen(),
      const SizedBox.shrink(),
      const PeopleScreen(),
      ProfilePage(reloadSignal: _profileShown),
    ];
  }

  @override
  void dispose() {
    _profileShown.dispose();
    super.dispose();
  }

  void _onTabChanged(int index) {
    if (index == _profileTab && index != _currentIndex) _profileShown.value++;
    setState(() => _currentIndex = index);
  }

  void _onFabPressed() {
    Navigator.pushNamed(context, '/create-event');
  }

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
        bottomNavigationBar: CustomBottomNavBar(
          currentIndex: _currentIndex,
          onTabChanged: _onTabChanged,
          onFabPressed: _onFabPressed,
        ),
      ),
    );
  }
}
