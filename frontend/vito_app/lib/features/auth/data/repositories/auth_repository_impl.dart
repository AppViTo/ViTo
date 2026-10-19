import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/error/exceptions.dart';
import '../../domain/entities/user.dart';
import '../../domain/repositories/auth_repository.dart';
import '../datasources/auth_remote_datasource.dart';
import '../datasources/auth_local_datasource.dart';
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
  }) {
    return _authenticate(
      () => remoteDataSource.login(email: email, password: password),
    );
  }

  @override
  Future<Either<Failure, User>> register({
    required String username,
    required String email,
    required String password,
    String? displayName,
  }) {
    return _authenticate(
      () => remoteDataSource.register(
        username: username,
        email: email,
        password: password,
        displayName: displayName,
      ),
    );
  }

  @override
  Future<Either<Failure, void>> logout() async {
    // La sesión local se cierra aunque el backend no responda (token ya
    // vencido, sin red): el usuario pidió salir.
    try {
      await remoteDataSource.logout();
    } catch (_) {
      // Ignorado a propósito: ver arriba.
    }

    try {
      await localDataSource.clearTokens();
      return const Right(null);
    } catch (e) {
      return Left(CacheFailure(message: e.toString()));
    }
  }

  @override
  Future<Either<Failure, User>> restoreSession() async {
    final String? refreshToken;
    try {
      refreshToken = await localDataSource.getRefreshToken();
    } catch (e) {
      return Left(CacheFailure(message: e.toString()));
    }

    if (refreshToken == null || refreshToken.isEmpty) {
      return const Left(AuthenticationFailure(message: 'No hay sesión guardada'));
    }

    final String token = refreshToken;
    final result = await _authenticate(
      () => remoteDataSource.refreshToken(refreshToken: token),
    );

    // Refresh rechazado: la sesión guardada ya no sirve. Sin red, se conserva
    // para reintentar en el próximo arranque.
    if (result.isLeft() &&
        result.fold((f) => f is AuthenticationFailure, (_) => false)) {
      await localDataSource.clearTokens();
    }
    return result;
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
    } on CacheException catch (e) {
      return Left(CacheFailure(message: e.message));
    } catch (e) {
      return Left(CacheFailure(message: e.toString()));
    }
  }

  @override
  Future<Either<Failure, String?>> getAccessToken() async {
    try {
      final token = await localDataSource.getAccessToken();

      return Right(token);
    } on CacheException catch (e) {
      return Left(CacheFailure(message: e.message));
    } catch (e) {
      return Left(CacheFailure(message: e.toString()));
    }
  }

  /// register/login/refresh: guarda los tokens (ADR-004) y devuelve el usuario.
  Future<Either<Failure, User>> _authenticate(
    Future<AuthResponseModel> Function() request,
  ) async {
    try {
      final auth = await request();

      await localDataSource.saveTokens(
        accessToken: auth.accessToken,
        refreshToken: auth.refreshToken,
      );
      await localDataSource.saveUserId(auth.user.id);

      return Right(auth.user);
    } catch (e) {
      return Left(_mapException(e));
    }
  }

  Failure _mapException(Object e) {
    if (e is ValidationException) return ValidationFailure(message: e.message);
    if (e is AuthenticationException) return AuthenticationFailure(message: e.message);
    if (e is AuthorizationException) return AuthorizationFailure(message: e.message);
    if (e is ConflictException) return ConflictFailure(message: e.message);
    if (e is NetworkException) return NetworkFailure(message: e.message);
    if (e is ServerException) return ServerFailure(message: e.message);
    if (e is CacheException) return CacheFailure(message: e.message);
    return ServerFailure(message: e.toString());
  }
}
