import 'package:dartz/dartz.dart';
import 'package:equatable/equatable.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/user.dart';
import '../repositories/auth_repository.dart';

class RegisterUseCase extends UseCase<User, RegisterParams> {
  final AuthRepository repository;
  
  RegisterUseCase(this.repository);
  
  @override
  Future<Either<Failure, User>> call(RegisterParams params) async {
    return await repository.register(
      username: params.username,
      email: params.email,
      password: params.password,
      displayName: params.displayName,
    );
  }
}

class RegisterParams extends Equatable {
  final String username;
  final String email;
  final String password;
  final String? displayName;
  
  const RegisterParams({
    required this.username,
    required this.email,
    required this.password,
    this.displayName,
  });
  
  @override
  List<Object> get props => [username, email, password, displayName ?? ''];
}
