import 'package:dartz/dartz.dart';

import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/features/social/domain/entities/social_profile.dart';
import 'package:vito_app/features/social/domain/entities/user_summary.dart';
import 'package:vito_app/features/social/domain/repositories/social_repository.dart';

/// Usuario de prueba `usr_N` con nombre "Persona N".
UserSummary person(int n, {bool withName = true}) => UserSummary(
      userId: 'usr_$n',
      username: 'persona$n',
      displayName: withName ? 'Persona $n' : null,
    );

/// Repositorio social en memoria para las listas del grafo y la búsqueda
/// (T053, T054). Las listas se sirven por páginas: [followerPages] y
/// [followingPages] dicen qué trae cada página; una página que no está en la
/// lista responde vacía.
class FakeGraphRepository implements SocialRepository {
  final List<List<UserSummary>> followerPages;
  final List<List<UserSummary>> followingPages;
  final List<UserSummary> searchResults;
  final Failure? listFailure;
  final Map<String, SocialProfile> profiles;

  /// Peticiones recibidas, p. ej. `following usr_1 0` o `search caro`.
  final List<String> calls = [];

  FakeGraphRepository({
    this.followerPages = const [],
    this.followingPages = const [],
    this.searchResults = const [],
    this.listFailure,
    this.profiles = const {},
  });

  Future<Either<Failure, List<UserSummary>>> _page(
    String name,
    List<List<UserSummary>> pages,
    String userId,
    int page,
  ) async {
    calls.add('$name $userId $page');
    final failure = listFailure;
    if (failure != null) return Left(failure);
    return Right(page < pages.length ? pages[page] : const []);
  }

  @override
  Future<Either<Failure, List<UserSummary>>> getFollowers({
    required String userId,
    int page = 0,
    int size = 20,
  }) =>
      _page('followers', followerPages, userId, page);

  @override
  Future<Either<Failure, List<UserSummary>>> getFollowing({
    required String userId,
    int page = 0,
    int size = 20,
  }) =>
      _page('following', followingPages, userId, page);

  @override
  Future<Either<Failure, List<UserSummary>>> searchUsers({
    required String query,
    int page = 0,
    int size = 20,
  }) async {
    calls.add('search $query');
    return Right(searchResults);
  }

  @override
  Future<Either<Failure, SocialProfile>> getProfile(String userId) async {
    calls.add('profile $userId');
    final profile = profiles[userId];
    return profile != null
        ? Right(profile)
        : const Left(NotFoundFailure(message: 'El usuario no existe'));
  }

  @override
  Future<Either<Failure, void>> follow(String userId) async => const Right(null);

  @override
  Future<Either<Failure, void>> unfollow(String userId) async =>
      const Right(null);
}
