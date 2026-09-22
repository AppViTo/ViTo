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
    String? displayName,
  });
  
  Future<Either<Failure, void>> logout();
  
  Future<Either<Failure, User>> refreshToken({
    required String refreshToken,
  });
  
  Future<Either<Failure, User>> getCurrentUser();
  
  Future<Either<Failure, bool>> isLoggedIn();
  
  Future<Either<Failure, String?>> getAccessToken();
}
