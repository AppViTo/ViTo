import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../entities/user.dart';

abstract class AuthRepository {
  Future<Either<Failure, User>> login({
    required String email,
    required String password,
  });

  Future<Either<Failure, User>> register({
    required String username,
    required String email,
    required String password,
  });

  Future<Either<Failure, void>> logout();

  /// Auto-login (ADR-004): `POST /auth/refresh` con el refresh token guardado.
  /// [AuthenticationFailure] si no hay sesión guardada o el backend la rechaza.
  Future<Either<Failure, User>> restoreSession();

  Future<Either<Failure, User>> getCurrentUser();

  Future<Either<Failure, bool>> isLoggedIn();

  Future<Either<Failure, String?>> getAccessToken();
}
