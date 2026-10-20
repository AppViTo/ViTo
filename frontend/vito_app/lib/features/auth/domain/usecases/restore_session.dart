import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/user.dart';
import '../repositories/auth_repository.dart';

/// Auto-login al arrancar la app (T-F01): usa el `refreshToken` persistido
/// para obtener un `AuthResponse` nuevo (`POST /auth/refresh`).
class RestoreSessionUseCase extends UseCase<User?, NoParams> {
  final AuthRepository repository;

  RestoreSessionUseCase(this.repository);

  @override
  Future<Either<Failure, User?>> call(NoParams params) {
    return repository.restoreSession();
  }
}
