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

  /// Auto-login al abrir la app (ADR-004, T060): con access token guardado,
  /// `GET /auth/me`; si responde 401 o no hay access token, `POST /auth/refresh`
  /// con el refresh token. [AuthenticationFailure] si no hay sesión guardada o
  /// el backend la rechaza (y entonces borra los tokens).
  Future<Either<Failure, User>> restoreSession();

  Future<Either<Failure, User>> getCurrentUser();

  Future<Either<Failure, bool>> isLoggedIn();

  Future<Either<Failure, String?>> getAccessToken();
}
