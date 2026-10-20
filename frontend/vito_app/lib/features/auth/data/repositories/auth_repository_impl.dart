import 'package:dartz/dartz.dart';

import '../../../../core/error/exceptions.dart';
import '../../../../core/error/failures.dart';
import '../../domain/entities/user.dart';
import '../../domain/repositories/auth_repository.dart';
import '../datasources/auth_local_datasource.dart';
import '../datasources/auth_remote_datasource.dart';
import '../models/auth_response_model.dart';

class AuthRepositoryImpl implements AuthRepository {
  final AuthRemoteDataSource remoteDataSource;
  final AuthLocalDataSource localDataSource;

  AuthRepositoryImpl({
    required this.remoteDataSource,
    required this.localDataSource,
  });

  @override
  Future<Either<Failure, User>> login({
    required String email,
    required String password,
  }) async {
    try {
      final session = await remoteDataSource.login(
        email: email,
        password: password,
      );

      await _persistSession(session);

      return Right(session.user);
    } catch (e) {
      return Left(_mapException(e));
    }
  }

  @override
  Future<Either<Failure, User>> register({
    required String username,
    required String email,
    required String password,
  }) async {
    try {
      final session = await remoteDataSource.register(
        username: username,
        email: email,
        password: password,
      );

      await _persistSession(session);

      return Right(session.user);
    } catch (e) {
      return Left(_mapException(e));
    }
  }

  @override
  Future<Either<Failure, void>> logout() async {
    try {
      await remoteDataSource.logout();
    } catch (e) {
      await localDataSource.clearTokens();
      return Left(_mapException(e));
    }

    await localDataSource.clearTokens();

    return const Right(null);
  }

  @override
  Future<Either<Failure, User>> refreshToken({
    required String refreshToken,
  }) async {
    try {
      final session = await remoteDataSource.refreshToken(
        refreshToken: refreshToken,
      );

      await _persistSession(session);

      return Right(session.user);
    } catch (e) {
      return Left(_mapException(e));
    }
  }

  @override
  Future<Either<Failure, User?>> restoreSession() async {
    final String? stored;
    try {
      stored = await localDataSource.getRefreshToken();
    } catch (e) {
      return Left(_mapException(e));
    }

    if (stored == null || stored.isEmpty) {
      return const Right(null);
    }

    try {
      final session = await remoteDataSource.refreshToken(
        refreshToken: stored,
      );

      await _persistSession(session);

      return Right(session.user);
    } on AuthenticationException {
      // Refresh inválido: la sesión venció. Se limpia y se arranca de cero.
      await localDataSource.clearTokens();
      return const Right(null);
    } catch (e) {
      return Left(_mapException(e));
    }
  }

  @override
  Future<Either<Failure, User>> getCurrentUser() async {
    try {
      final user = await remoteDataSource.getCurrentUser();

      return Right(user);
    } catch (e) {
      return Left(_mapException(e));
    }
  }

  @override
  Future<Either<Failure, bool>> isLoggedIn() async {
    try {
      final isLoggedIn = await localDataSource.isLoggedIn();

      return Right(isLoggedIn);
    } catch (e) {
      return Left(_mapException(e));
    }
  }

  @override
  Future<Either<Failure, String?>> getAccessToken() async {
    try {
      final token = await localDataSource.getAccessToken();

      return Right(token);
    } catch (e) {
      return Left(_mapException(e));
    }
  }

  Future<void> _persistSession(AuthResponseModel session) async {
    await localDataSource.saveTokens(
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
    );
    await localDataSource.saveUserId(session.user.id);
  }

  Failure _mapException(Object error) {
    if (error is AuthenticationException) {
      return AuthenticationFailure(message: error.message);
    }
    if (error is ValidationException) {
      return ValidationFailure(message: error.message);
    }
    if (error is NetworkException) {
      return NetworkFailure(message: error.message);
    }
    if (error is CacheException) {
      return CacheFailure(message: error.message);
    }
    if (error is ServerException) {
      return ServerFailure(message: error.message);
    }
    return ServerFailure(message: error.toString());
  }
}
