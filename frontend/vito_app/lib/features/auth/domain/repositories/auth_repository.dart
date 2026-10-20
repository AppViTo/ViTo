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
  
  Future<Either<Failure, User>> refreshToken({
    required String refreshToken,
  });
  
  /// Auto-login: intercambia el `refreshToken` guardado por un nuevo par de
  /// tokens y el `user` del `AuthResponse` (ADR-004). `Right(null)` significa
  /// que no hay sesión guardada o que venció; `Left` solo para errores
  /// distintos de 401.
  Future<Either<Failure, User?>> restoreSession();
  
  Future<Either<Failure, User>> getCurrentUser();
  
  Future<Either<Failure, bool>> isLoggedIn();
  
  Future<Either<Failure, String?>> getAccessToken();
}
