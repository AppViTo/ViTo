import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../data/models/home_models.dart';
import '../../data/services/mock_api_service.dart';
import '../widgets/header_greetings.dart';
import '../widgets/search_bar.dart';
import '../widgets/event_card_home.dart';
import '../widgets/live_stream_widget.dart';
import '../widgets/active_friends_section.dart';
import '../widgets/story_card.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final MockApiService _api = MockApiService();
  bool _isLoading = true;
  String _searchQuery = '';

  HomeUser? _user;
  List<HomeEvent> _events = [];
  List<LiveStream> _liveStreams = [];
  List<ActiveFriend> _activeFriends = [];
  List<Story> _stories = [];

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() => _isLoading = true);
    try {
      final results = await Future.wait([
        _api.getCurrentUser(),
        _api.getUpcomingEvents(),
        _api.getLiveStreams(),
        _api.getActiveFriends(),
        _api.getStories(),
      ]);
      if (mounted) {
        setState(() {
          _user = results[0] as HomeUser;
          _events = results[1] as List<HomeEvent>;
          _liveStreams = results[2] as List<LiveStream>;
          _activeFriends = results[3] as List<ActiveFriend>;
          _stories = results[4] as List<Story>;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
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
        physics: const BouncingScrollPhysics(),
        slivers: [
          SliverToBoxAdapter(
            child: HeaderGreetings(
              user: _user ?? HomeUser.mock(),
              onNotificationTap: () {},
            ),
          ),
          SliverToBoxAdapter(
            child: HomeSearchBar(
              onChanged: (value) => setState(() => _searchQuery = value),
              onFilterTap: () {},
            ),
          ),
          if (_stories.isNotEmpty) ...[
            const SliverToBoxAdapter(child: SizedBox(height: 8)),
            SliverToBoxAdapter(
              child: _buildSectionTitle('Historias'),
            ),
            SliverToBoxAdapter(
              child: _buildStoriesCarousel(),
            ),
          ],
          if (_liveStreams.isNotEmpty) ...[
            const SliverToBoxAdapter(child: SizedBox(height: 8)),
            SliverToBoxAdapter(
              child: _buildSectionTitle('Transmitiendo ahora'),
            ),
            SliverList(
              delegate: SliverChildBuilderDelegate(
                (context, index) => Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: LiveStreamWidget(
                    stream: _liveStreams[index],
                    onTap: () {},
                  ),
                ),
                childCount: _liveStreams.length,
              ),
            ),
          ],
          const SliverToBoxAdapter(child: SizedBox(height: 8)),
          SliverToBoxAdapter(
            child: _buildSectionTitle('Próximos eventos'),
          ),
          SliverToBoxAdapter(
            child: _buildEventsCarousel(),
          ),
          const SliverToBoxAdapter(child: SizedBox(height: 8)),
          if (_activeFriends.isNotEmpty)
            SliverToBoxAdapter(
              child: ActiveFriendsSection(friends: _activeFriends),
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

  Widget _buildStoriesCarousel() {
    return LayoutBuilder(
      builder: (context, constraints) {
        final totalWidth = constraints.maxWidth * 0.90;
        final cardWidth = (totalWidth - 18) / 4;

        return SizedBox(
          height: 140,
          child: ListView.separated(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            scrollDirection: Axis.horizontal,
            itemCount: _stories.length + 1,
            separatorBuilder: (_, __) => const SizedBox(width: 6),
            itemBuilder: (context, index) {
              if (index == 0) {
                return SizedBox(
                  width: cardWidth,
                  child: _buildCreateStoryCard(),
                );
              }
              return SizedBox(
                width: cardWidth,
                child: StoryCard(
                  story: _stories[index - 1],
                  onTap: () {},
                ),
              );
            },
          ),
        );
      },
    );
  }

  Widget _buildCreateStoryCard() {
    return GestureDetector(
      onTap: () {},
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Container(
            width: double.infinity,
            height: double.infinity,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(16),
              gradient: LinearGradient(
                colors: [
                  AppColors.coralNeon.withValues(alpha: 0.15),
                  AppColors.lavandaPale.withValues(alpha: 0.1),
                ],
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
              ),
              border: Border.all(
                color: AppColors.grey800.withValues(alpha: 0.4),
                width: 1.5,
              ),
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: const BoxDecoration(
                    shape: BoxShape.circle,
                    color: AppColors.surfaceDark,
                  ),
                  child: const Center(
                    child: Icon(
                      Icons.person_outline_rounded,
                      size: 28,
                      color: AppColors.textSecondary,
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Tu historia',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                    color: AppColors.textSecondary,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textAlign: TextAlign.center,
                ),
              ],
            ),
          ),
          Positioned(
            bottom: 10,
            right: -4,
            child: Container(
              width: 28,
              height: 28,
              decoration: const BoxDecoration(
                shape: BoxShape.circle,
                color: AppColors.coralNeon,
                boxShadow: [
                  BoxShadow(
                    color: AppColors.coralNeon,
                    blurRadius: 8,
                    spreadRadius: 1,
                  ),
                ],
              ),
              child: const Icon(
                Icons.add_rounded,
                size: 18,
                color: AppColors.blancoHielo,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildEventsCarousel() {
    final events = _filteredEvents;
    if (events.isEmpty) {
      return _buildEmptyEvents();
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

  Widget _buildEmptyEvents() {
    return Container(
      height: 200,
      margin: const EdgeInsets.symmetric(horizontal: 24),
      decoration: BoxDecoration(
        color: const Color(0xFF161D2F),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: AppColors.grey800.withValues(alpha: 0.3),
          width: 1,
        ),
      ),
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.event_busy_rounded,
              size: 40,
              color: AppColors.textSecondary.withValues(alpha: 0.3),
            ),
            const SizedBox(height: 12),
            Text(
              'No hay eventos próximos',
              style: TextStyle(
                fontSize: 14,
                color: AppColors.textSecondary.withValues(alpha: 0.7),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
