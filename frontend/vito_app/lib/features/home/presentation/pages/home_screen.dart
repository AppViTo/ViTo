import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../../core/di/injection_container.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../auth/presentation/bloc/auth_bloc.dart';
import '../../../events/domain/entities/event.dart';
import '../../../events/domain/repositories/event_repository.dart';
import '../../data/models/home_models.dart';
import '../widgets/header_greetings.dart';
import '../widgets/search_bar.dart';
import '../widgets/event_card_home.dart';

class HomeScreen extends StatefulWidget {
  /// Por defecto, el repositorio registrado en `sl`. Los tests pasan uno falso.
  final EventRepository? eventRepository;

  const HomeScreen({super.key, this.eventRepository});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  late final EventRepository _eventRepository =
      widget.eventRepository ?? sl<EventRepository>();
  bool _isLoading = true;
  bool _eventsFailed = false;
  String _searchQuery = '';

  List<HomeEvent> _events = [];

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  /// Próximos eventos visibles para el usuario más los que organiza
  /// (`listEvents` con `scope=upcoming` y `scope=mine`, ADR-014).
  /// Amigos, historias y transmisiones aún no tienen contrato (Olas 1–2):
  /// se muestran vacíos en vez de datos de ejemplo.
  Future<void> _loadData() async {
    setState(() => _isLoading = true);
    final results = await Future.wait([
      _eventRepository.getUpcomingEvents(),
      _eventRepository.getMyEvents(),
    ]);
    if (!mounted) return;

    final byId = <String, Event>{};
    var failures = 0;
    for (final result in results) {
      result.fold(
        (_) => failures++,
        (events) {
          for (final event in events) {
            byId[event.id] = event;
          }
        },
      );
    }

    final now = DateTime.now();
    final events = byId.values
        .where((e) =>
            e.status != EventStatus.cancelled &&
            e.status != EventStatus.completed)
        .where((e) {
          final end = e.details?.endDate ?? e.details?.startDate;
          return end == null || end.isAfter(now);
        })
        .map(_toHomeEvent)
        .toList()
      ..sort((a, b) => a.date.compareTo(b.date));

    setState(() {
      _events = events;
      _eventsFailed = failures == results.length;
      _isLoading = false;
    });
  }

  HomeEvent _toHomeEvent(Event event) {
    final details = event.details;
    final covers = details?.coverImages;
    return HomeEvent(
      id: event.id,
      title: event.title,
      coverImage: covers != null && covers.isNotEmpty ? covers.first : null,
      date: details?.startDate ?? event.createdAt ?? DateTime.now(),
      location: details?.location,
      category: details?.category ?? 'general',
      attendeeCount: event.stats?.totalAccepted ?? 0,
    );
  }

  HomeUser _currentUser(AuthState state) {
    if (state is AuthAuthenticated) {
      final user = state.user;
      final displayName = user.profile?.displayName;
      return HomeUser(
        id: user.id,
        displayName: displayName != null && displayName.isNotEmpty
            ? displayName
            : user.username,
        username: '@${user.username}',
        avatar: user.profile?.avatar,
      );
    }
    return const HomeUser(id: '', displayName: '');
  }

  List<HomeEvent> get _filteredEvents {
    if (_searchQuery.isEmpty) return _events;
    return _events.where((e) =>
      e.title.toLowerCase().contains(_searchQuery.toLowerCase()) ||
      (e.location?.toLowerCase().contains(_searchQuery.toLowerCase()) ?? false)
    ).toList();
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Center(
        child: CircularProgressIndicator(color: AppColors.coralNeon),
      );
    }

    return RefreshIndicator(
      onRefresh: _loadData,
      color: AppColors.coralNeon,
      backgroundColor: AppColors.surfaceDark,
      child: CustomScrollView(
        physics: const BouncingScrollPhysics(
          parent: AlwaysScrollableScrollPhysics(),
        ),
        slivers: [
          SliverToBoxAdapter(
            child: BlocBuilder<AuthBloc, AuthState>(
              builder: (context, state) => HeaderGreetings(
                user: _currentUser(state),
                onNotificationTap: () {},
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: HomeSearchBar(
              onChanged: (value) => setState(() => _searchQuery = value),
              onFilterTap: () {},
            ),
          ),
          const SliverToBoxAdapter(child: SizedBox(height: 8)),
          SliverToBoxAdapter(
            child: _buildSectionTitle('Próximos eventos'),
          ),
          SliverToBoxAdapter(
            child: _buildEventsCarousel(),
          ),
          const SliverToBoxAdapter(child: SizedBox(height: 8)),
          SliverToBoxAdapter(
            child: _buildSectionTitle('Transmitiendo ahora'),
          ),
          SliverToBoxAdapter(
            child: _buildEmptyState(
              icon: Icons.live_tv_rounded,
              message: 'No hay transmisiones en vivo de tus eventos',
            ),
          ),
          const SliverToBoxAdapter(child: SizedBox(height: 8)),
          SliverToBoxAdapter(
            child: _buildSectionTitle('Historias'),
          ),
          SliverToBoxAdapter(
            child: _buildEmptyState(
              icon: Icons.auto_stories_rounded,
              message: 'Aún no hay historias por mostrar',
            ),
          ),
          const SliverToBoxAdapter(child: SizedBox(height: 8)),
          SliverToBoxAdapter(
            child: _buildSectionTitle('Amigos activos'),
          ),
          SliverToBoxAdapter(
            child: _buildEmptyState(
              icon: Icons.people_outline_rounded,
              message: 'Aún no tienes amigos',
            ),
          ),
          const SliverToBoxAdapter(child: SizedBox(height: 100)),
        ],
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 8, 24, 12),
      child: Text(
        title,
        style: const TextStyle(
          fontSize: 16,
          fontWeight: FontWeight.w700,
          color: AppColors.blancoHielo,
        ),
      ),
    );
  }

  Widget _buildEventsCarousel() {
    final events = _filteredEvents;
    if (events.isEmpty) {
      return _buildEmptyState(
        icon: Icons.event_busy_rounded,
        message: _eventsFailed
            ? 'No pudimos cargar tus eventos. Desliza hacia abajo para reintentar.'
            : _searchQuery.isNotEmpty
                ? 'No hay eventos que coincidan con tu búsqueda'
                : 'Aún no tienes eventos para asistir',
      );
    }

    return LayoutBuilder(
      builder: (context, constraints) {
        final totalWidth = constraints.maxWidth * 0.90;
        final cardWidth = (totalWidth - 16) / 2;

        return SizedBox(
          height: 260,
          child: ListView.separated(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            scrollDirection: Axis.horizontal,
            itemCount: events.length,
            separatorBuilder: (_, __) => const SizedBox(width: 12),
            itemBuilder: (context, index) {
              return SizedBox(
                width: cardWidth,
                child: EventCardHome(
                  event: events[index],
                  onTap: () {
                    Navigator.pushNamed(
                      context,
                      '/event-detail',
                      arguments: {'eventId': events[index].id},
                    );
                  },
                ),
              );
            },
          ),
        );
      },
    );
  }

  Widget _buildEmptyState({required IconData icon, required String message}) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 28),
      margin: const EdgeInsets.symmetric(horizontal: 24),
      decoration: BoxDecoration(
        color: const Color(0xFF161D2F),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: AppColors.grey800.withValues(alpha: 0.3),
          width: 1,
        ),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            icon,
            size: 40,
            color: AppColors.textSecondary.withValues(alpha: 0.3),
          ),
          const SizedBox(height: 12),
          Text(
            message,
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 14,
              color: AppColors.textSecondary.withValues(alpha: 0.7),
            ),
          ),
        ],
      ),
    );
  }
}
