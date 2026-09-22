import '../models/home_models.dart';

class MockApiService {
  static final MockApiService _instance = MockApiService._();
  factory MockApiService() => _instance;
  MockApiService._();

  Future<HomeUser> getCurrentUser() async {
    await Future.delayed(const Duration(seconds: 1));
    return HomeUser.mock();
  }

  Future<List<HomeEvent>> getUpcomingEvents() async {
    await Future.delayed(const Duration(seconds: 1));
    final now = DateTime.now();
    return [
      HomeEvent(
        id: 'e1',
        title: 'Fiesta de Cumpleaños',
        date: now.add(const Duration(days: 2)),
        location: 'Restaurante La Terraza',
        category: 'party',
        attendeeCount: 24,
        guests: const [
          HomeGuest(id: 'g1', displayName: 'María', isOnline: true),
          HomeGuest(id: 'g2', displayName: 'Carlos', isOnline: true),
          HomeGuest(id: 'g3', displayName: 'Laura', isOnline: false),
        ],
      ),
      HomeEvent(
        id: 'e2',
        title: 'Concierto Acústico',
        date: now.add(const Duration(days: 5)),
        location: 'Parque Central',
        category: 'music',
        isLive: true,
        attendeeCount: 156,
        guests: const [
          HomeGuest(id: 'g4', displayName: 'Pedro', isOnline: true),
          HomeGuest(id: 'g5', displayName: 'Ana', isOnline: true),
          HomeGuest(id: 'g6', displayName: 'Diego', isOnline: true),
          HomeGuest(id: 'g7', displayName: 'Sofia', isOnline: false),
        ],
      ),
      HomeEvent(
        id: 'e3',
        title: 'After Office',
        date: now.add(const Duration(days: 8)),
        location: 'Bar El Rincón',
        category: 'social',
        attendeeCount: 42,
        guests: const [
          HomeGuest(id: 'g8', displayName: 'Roberto', isOnline: false),
          HomeGuest(id: 'g9', displayName: 'Carmen', isOnline: true),
        ],
      ),
      HomeEvent(
        id: 'e4',
        title: 'Boda Andrea & Luis',
        date: now.add(const Duration(days: 15)),
        location: 'Hacienda San José',
        category: 'wedding',
        attendeeCount: 120,
        guests: const [
          HomeGuest(id: 'g10', displayName: 'Jorge', isOnline: true),
          HomeGuest(id: 'g11', displayName: 'Elena', isOnline: false),
          HomeGuest(id: 'g12', displayName: 'Miguel', isOnline: true),
        ],
      ),
    ];
  }

  Future<List<LiveStream>> getLiveStreams() async {
    await Future.delayed(const Duration(seconds: 1));
    final now = DateTime.now();
    return [
      LiveStream(
        id: 'ls1',
        title: 'Concierto en vivo',
        hostName: 'Carlos Méndez',
        viewerCount: 89,
        eventTitle: 'Concierto Acústico',
        startedAt: now.subtract(const Duration(minutes: 23)),
      ),
      LiveStream(
        id: 'ls2',
        title: 'Preparando la fiesta',
        hostName: 'María López',
        viewerCount: 34,
        eventTitle: 'Fiesta de Cumpleaños',
        startedAt: now.subtract(const Duration(minutes: 8)),
      ),
    ];
  }

  Future<List<ActiveFriend>> getActiveFriends() async {
    await Future.delayed(const Duration(seconds: 1));
    return const [
      ActiveFriend(id: 'f1', displayName: 'María', currentActivity: 'En concierto', isOnline: true),
      ActiveFriend(id: 'f2', displayName: 'Carlos', currentActivity: 'Subiendo fotos', isOnline: true),
      ActiveFriend(id: 'f3', displayName: 'Laura', isOnline: true),
      ActiveFriend(id: 'f4', displayName: 'Pedro', currentActivity: 'En stream', isOnline: true),
      ActiveFriend(id: 'f5', displayName: 'Ana', isOnline: false),
      ActiveFriend(id: 'f6', displayName: 'Diego', currentActivity: 'Comentando', isOnline: true),
      ActiveFriend(id: 'f7', displayName: 'Sofia', isOnline: true),
    ];
  }

  Future<List<AppNotification>> getNotifications() async {
    await Future.delayed(const Duration(seconds: 1));
    final now = DateTime.now();
    return [
      AppNotification(id: 'n1', title: 'Nuevo RSVP', body: 'María aceptó tu invitación', timestamp: now.subtract(const Duration(minutes: 5)), type: 'rsvp'),
      AppNotification(id: 'n2', title: 'Stream en vivo', body: 'Carlos está transmitiendo', timestamp: now.subtract(const Duration(minutes: 15)), type: 'stream'),
      AppNotification(id: 'n3', title: 'Nuevo comentario', body: 'Laura comentó en tu evento', timestamp: now.subtract(const Duration(hours: 1)), type: 'comment'),
    ];
  }

  Future<List<Story>> getStories() async {
    await Future.delayed(const Duration(seconds: 1));
    final now = DateTime.now();
    return [
      Story(id: 's1', userId: 'u2', userName: 'María', timestamp: now.subtract(const Duration(minutes: 10)), isViewed: false),
      Story(id: 's2', userId: 'u3', userName: 'Carlos', timestamp: now.subtract(const Duration(minutes: 25)), isViewed: false),
      Story(id: 's3', userId: 'u4', userName: 'Laura', timestamp: now.subtract(const Duration(hours: 1)), isViewed: true),
      Story(id: 's4', userId: 'u5', userName: 'Pedro', timestamp: now.subtract(const Duration(hours: 2)), isViewed: false),
      Story(id: 's5', userId: 'u6', userName: 'Ana', timestamp: now.subtract(const Duration(hours: 3)), isViewed: true),
      Story(id: 's6', userId: 'u7', userName: 'Diego', timestamp: now.subtract(const Duration(hours: 4)), isViewed: false),
      Story(id: 's7', userId: 'u8', userName: 'Sofia', timestamp: now.subtract(const Duration(hours: 5)), isViewed: true),
      Story(id: 's8', userId: 'u9', userName: 'Roberto', timestamp: now.subtract(const Duration(hours: 6)), isViewed: false),
    ];
  }
}
