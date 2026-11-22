import 'package:dartz/dartz.dart';

import '../../../../core/error/exceptions.dart';
import '../../../../core/error/failures.dart';
import '../datasources/social_remote_datasource.dart';
import '../datasources/user_remote_datasource.dart';
import '../../domain/entities/social_profile.dart';
import '../../domain/entities/user_summary.dart';
import '../../domain/repositories/social_repository.dart';

/// Mensaje cuando `social-service` no puede completar la identidad porque
/// `auth-service` no responde (`503`, T048).
const profileUnavailableMessage = 'No pudimos cargar el perfil, intenta de nuevo';

class SocialRepositoryImpl implements SocialRepository {
  final SocialRemoteDataSource remoteDataSource;

  /// Búsqueda de usuarios de `auth-service` (Ola 2a, T034).
  final UserRemoteDataSource userRemoteDataSource;

  SocialRepositoryImpl({
    required this.remoteDataSource,
    required this.userRemoteDataSource,
  });

  /// Traduce las excepciones del datasource a fallos con el mensaje del
  /// backend: 400 (p. ej. seguirse a uno mismo), 401, 404, 503 y sin red.
  Failure _toFailure(Object error, {String? unavailableMessage}) {
    if (error is ValidationException) {
      return ValidationFailure(message: error.message);
    }
    if (error is AuthenticationException) {
      return AuthenticationFailure(message: error.message);
    }
    if (error is NetworkException) return NetworkFailure(message: error.message);
    if (error is ServerException) {
      switch (error.statusCode) {
        case 404:
          return NotFoundFailure(message: error.message);
        case 503:
          return ServerFailure(message: unavailableMessage ?? error.message);
      }
      return ServerFailure(message: error.message);
    }
    return ServerFailure(message: error.toString());
  }

  Future<Either<Failure, T>> _guard<T>(
    Future<T> Function() call, {
    String? unavailableMessage,
  }) async {
    try {
      return Right(await call());
    } catch (e) {
      return Left(_toFailure(e, unavailableMessage: unavailableMessage));
    }
  }

  /// `followUser`: idempotente, `204` también si ya lo seguía.
  @override
  Future<Either<Failure, void>> follow(String userId) =>
      _guard(() => remoteDataSource.follow(userId));

  /// `unfollowUser`: idempotente, `204` también si no lo seguía.
  @override
  Future<Either<Failure, void>> unfollow(String userId) =>
      _guard(() => remoteDataSource.unfollow(userId));

  @override
  Future<Either<Failure, SocialProfile>> getProfile(String userId) => _guard(
        () => remoteDataSource.getProfile(userId),
        unavailableMessage: profileUnavailableMessage,
      );

  @override
  Future<Either<Failure, List<UserSummary>>> getFollowers({
    required String userId,
    int page = 0,
    int size = 20,
  }) =>
      _guard(() => remoteDataSource.getFollowers(
            userId: userId,
            page: page,
            size: size,
          ));

  @override
  Future<Either<Failure, List<UserSummary>>> getFollowing({
    required String userId,
    int page = 0,
    int size = 20,
  }) =>
      _guard(() => remoteDataSource.getFollowing(
            userId: userId,
            page: page,
            size: size,
          ));

  @override
  Future<Either<Failure, List<UserSummary>>> searchUsers({
    required String query,
    int page = 0,
    int size = 20,
  }) =>
      _guard(() => userRemoteDataSource.searchUsers(
            query: query,
            page: page,
            size: size,
          ));
}
