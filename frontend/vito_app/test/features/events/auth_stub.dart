import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/features/auth/domain/entities/user.dart';
import 'package:vito_app/features/auth/domain/repositories/auth_repository.dart';
import 'package:vito_app/features/auth/domain/usecases/login.dart';
import 'package:vito_app/features/auth/domain/usecases/logout.dart';
import 'package:vito_app/features/auth/domain/usecases/register.dart';
import 'package:vito_app/features/auth/domain/usecases/restore_session.dart';
import 'package:vito_app/features/auth/presentation/bloc/auth_bloc.dart';

class _UnusedAuthRepository extends Fake implements AuthRepository {}

/// `AuthBloc` ya resuelto: autenticado como [userId] o, si es `null`, sin
/// sesión.
class StubAuthBloc extends AuthBloc {
  StubAuthBloc(String? userId)
      : super(
          loginUseCase: LoginUseCase(_UnusedAuthRepository()),
          registerUseCase: RegisterUseCase(_UnusedAuthRepository()),
          logoutUseCase: LogoutUseCase(_UnusedAuthRepository()),
          restoreSessionUseCase: RestoreSessionUseCase(_UnusedAuthRepository()),
        ) {
    emit(userId == null
        ? const AuthUnauthenticated()
        : AuthAuthenticated(
            user: User(id: userId, username: 'user', email: 'user@vito.app'),
          ));
  }
}
