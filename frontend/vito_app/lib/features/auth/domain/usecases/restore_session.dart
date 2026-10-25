import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/user.dart';
import '../repositories/auth_repository.dart';

/// Auto-login al abrir la app con el refresh token guardado (ADR-004).
class RestoreSessionUseCase extends UseCase<User, NoParams> {
  final AuthRepository repository;

  RestoreSessionUseCase(this.repository);

  @override
  Future<Either<Failure, User>> call(NoParams params) async {
    return await repository.restoreSession();
  }
}
