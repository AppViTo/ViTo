import 'package:dartz/dartz.dart';

import 'package:vito_app/core/error/exceptions.dart';
import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/features/auth/data/datasources/auth_local_datasource.dart';
import 'package:vito_app/features/auth/data/datasources/auth_remote_datasource.dart';
import 'package:vito_app/features/auth/data/models/auth_response_model.dart';
import 'package:vito_app/features/auth/data/models/user_model.dart';
import 'package:vito_app/features/auth/domain/entities/user.dart';
import 'package:vito_app/features/auth/domain/repositories/auth_repository.dart';

/// Sesión de ejemplo: los mismos valores de `authentication/*.json`.
const String kAccessToken = 'access-token-abc';
const String kRefreshToken = 'refresh-token-xyz';

const AuthResponseModel kSession = AuthResponseModel(
  user: UserModel(id: 'usr_01H8X2K4QJ', username: 'alex_villa', email: 'alex@vito.com'),
  accessToken: kAccessToken,
  refreshToken: kRefreshToken,
);

/// Fake de `AuthRepository` para los tests de `AuthBloc` y de los use cases.
class FakeAuthRepository implements AuthRepository {
  static const User testUser = User(
    id: 'usr_01H8X2K4QJ',
    username: 'alex_villa',
    email: 'alex@vito.com',
  );

  Either<Failure, User> loginOutcome;
  Either<Failure, User> registerOutcome;
  Either<Failure, User?> restoreOutcome;
  Either<Failure, void> logoutOutcome;

  final List<String> calls = [];

  FakeAuthRepository({
    this.loginOutcome = const Right(testUser),
    this.registerOutcome = const Right(testUser),
    this.restoreOutcome = const Right(null),
    this.logoutOutcome = const Right(null),
  });

  @override
  Future<Either<Failure, User>> login({
    required String email,
    required String password,
  }) async {
    calls.add('login:$email');
    return loginOutcome;
  }

  @override
  Future<Either<Failure, User>> register({
    required String username,
    required String email,
    required String password,
  }) async {
    calls.add('register:$username');
    return registerOutcome;
  }

  @override
  Future<Either<Failure, void>> logout() async {
    calls.add('logout');
    return logoutOutcome;
  }

  @override
  Future<Either<Failure, User>> refreshToken({
    required String refreshToken,
  }) async {
    calls.add('refresh:$refreshToken');
    return restoreOutcome.fold(
      (failure) => Left(failure),
      (user) => Right(user ?? testUser),
    );
  }

  @override
  Future<Either<Failure, User?>> restoreSession() async {
    calls.add('restoreSession');
    return restoreOutcome;
  }

  @override
  Future<Either<Failure, User>> getCurrentUser() async => const Left(
        ServerFailure(message: 'No usado en T-F01 (GET /auth/me es Ola 1)'),
      );

  @override
  Future<Either<Failure, bool>> isLoggedIn() async => const Right(true);

  @override
  Future<Either<Failure, String?>> getAccessToken() async =>
      const Right(kAccessToken);
}

/// Fake del datasource HTTP: lanza o devuelve lo que se le indique y registra
/// las llamadas.
class FakeAuthRemoteDataSource implements AuthRemoteDataSource {
  AuthResponseModel loginResult = kSession;
  Object? loginError;

  AuthResponseModel registerResult = kSession;
  Object? registerError;

  AuthResponseModel refreshResult = kSession;
  Object? refreshError;

  Object? logoutError;

  final List<String> calls = [];
  final List<String> refreshTokens = [];

  @override
  Future<AuthResponseModel> login({
    required String email,
    required String password,
  }) async {
    calls.add('login');
    final error = loginError;
    if (error != null) throw error;
    return loginResult;
  }

  @override
  Future<AuthResponseModel> register({
    required String username,
    required String email,
    required String password,
  }) async {
    calls.add('register');
    final error = registerError;
    if (error != null) throw error;
    return registerResult;
  }

  @override
  Future<void> logout() async {
    calls.add('logout');
    final error = logoutError;
    if (error != null) throw error;
  }

  @override
  Future<AuthResponseModel> refreshToken({
    required String refreshToken,
  }) async {
    calls.add('refresh');
    refreshTokens.add(refreshToken);
    final error = refreshError;
    if (error != null) throw error;
    return refreshResult;
  }

  @override
  Future<UserModel> getCurrentUser() async =>
      throw UnimplementedError('GET /auth/me es Ola 1');
}

/// Fake del almacenamiento local en memoria.
class FakeAuthLocalDataSource implements AuthLocalDataSource {
  String? accessToken;
  String? refreshToken;
  String? userId;
  bool throwOnRead = false;

  final List<String> ops = [];

  @override
  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    ops.add('saveTokens');
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
  }

  @override
  Future<String?> getAccessToken() async {
    ops.add('getAccessToken');
    _throwIfConfigured();
    return accessToken;
  }

  @override
  Future<String?> getRefreshToken() async {
    ops.add('getRefreshToken');
    _throwIfConfigured();
    return refreshToken;
  }

  @override
  Future<void> clearTokens() async {
    ops.add('clearTokens');
    accessToken = null;
    refreshToken = null;
    userId = null;
  }

  @override
  Future<bool> isLoggedIn() async => accessToken != null;

  @override
  Future<void> saveUserId(String userId) async {
    ops.add('saveUserId');
    this.userId = userId;
  }

  @override
  Future<String?> getUserId() async => userId;

  void _throwIfConfigured() {
    if (throwOnRead) {
      throw const CacheException(message: 'Storage inaccesible');
    }
  }
}
