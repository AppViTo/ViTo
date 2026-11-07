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
  bool _myEventsFailed = false;
  bool _upcomingEventsFailed = false;
  String _searchQuery = '';

  List<HomeEvent> _myEvents = [];
  List<HomeEvent> _upcomingEvents = [];

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  /// Eventos que organiza el usuario (`scope=mine`) y próximos eventos
  /// visibles (`scope=upcoming`, ADR-014), en secciones separadas.
  /// Los propios no se repiten en "Próximos eventos".
  /// Amigos, historias y transmisiones aún no tienen contrato (Olas 1–2):
  /// se muestran vacíos en vez de datos de ejemplo.
  Future<void> _loadData() async {
    setState(() => _isLoading = true);
    final results = await Future.wait([
      _eventRepository.getMyEvents(),
      _eventRepository.getUpcomingEvents(),
    ]);
    if (!mounted) return;

    final myById = <String, Event>{};
    final upcomingById = <String, Event>{};
    var myFailed = false;
    var upcomingFailed = false;

    results[0].fold(
      (_) => myFailed = true,
      (events) {
        for (final event in events) {
          myById[event.id] = event;
        }
      },
    );
    results[1].fold(
      (_) => upcomingFailed = true,
      (events) {
        for (final event in events) {
          upcomingById[event.id] = event;
        }
      },
    );

    final myEvents = _visibleEvents(myById);
    final myIds = myEvents.map((event) => event.id).toSet();
    final upcomingEvents = _visibleEvents(upcomingById)
        .where((event) => !myIds.contains(event.id))
        .toList();

    setState(() {
      _myEvents = myEvents;
      _upcomingEvents = upcomingEvents;
      _myEventsFailed = myFailed;
      _upcomingEventsFailed = upcomingFailed;
      _isLoading = false;
    });
  }

  List<HomeEvent> _visibleEvents(Map<String, Event> byId) {
    final now = DateTime.now();
    return byId.values
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

  List<HomeEvent> _filtered(List<HomeEvent> events) {
    if (_searchQuery.isEmpty) return events;
    final query = _searchQuery.toLowerCase();
    return events
        .where((e) =>
            e.title.toLowerCase().contains(query) ||
            (e.location?.toLowerCase().contains(query) ?? false))
        .toList();
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
            child: _buildSectionTitle('Mis eventos'),
          ),
          SliverToBoxAdapter(
            child: _buildEventsCarousel(
              events: _filtered(_myEvents),
              failed: _myEventsFailed,
              emptyMessage: _myEvents.isEmpty
                  ? 'Aún no has creado eventos'
                  : 'No hay eventos que coincidan con tu búsqueda',
              failureMessage:
                  'No pudimos cargar tus eventos. Desliza hacia abajo para reintentar.',
            ),
          ),
          const SliverToBoxAdapter(child: SizedBox(height: 8)),
          SliverToBoxAdapter(
            child: _buildSectionTitle('Próximos eventos'),
          ),
          SliverToBoxAdapter(
            child: _buildEventsCarousel(
              events: _filtered(_upcomingEvents),
              failed: _upcomingEventsFailed,
              emptyMessage: _upcomingEvents.isEmpty
                  ? 'Aún no tienes eventos para asistir'
                  : 'No hay eventos que coincidan con tu búsqueda',
              failureMessage:
                  'No pudimos cargar los próximos eventos. Desliza hacia abajo para reintentar.',
            ),
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

  Widget _buildEventsCarousel({
    required List<HomeEvent> events,
    required bool failed,
    required String emptyMessage,
    required String failureMessage,
  }) {
    if (events.isEmpty) {
      return _buildEmptyState(
        icon: Icons.event_busy_rounded,
        message: failed ? failureMessage : emptyMessage,
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
