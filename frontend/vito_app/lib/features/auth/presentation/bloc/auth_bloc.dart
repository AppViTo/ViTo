import 'dart:async';

import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:equatable/equatable.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/network/session_expired_notifier.dart';
import '../../../../core/usecase/usecase.dart';
import '../../domain/entities/user.dart';
import '../../domain/usecases/login.dart';
import '../../domain/usecases/register.dart';
import '../../domain/usecases/logout.dart';
import '../../domain/usecases/restore_session.dart';

// Events
abstract class AuthEvent extends Equatable {
  const AuthEvent();
  
  @override
  List<Object> get props => [];
}

class LoginRequested extends AuthEvent {
  final String email;
  final String password;
  
  const LoginRequested({
    required this.email,
    required this.password,
  });
  
  @override
  List<Object> get props => [email, password];
}

class RegisterRequested extends AuthEvent {
  final String username;
  final String email;
  final String password;
  final String? displayName;
  
  const RegisterRequested({
    required this.username,
    required this.email,
    required this.password,
    this.displayName,
  });
  
  @override
  List<Object> get props => [username, email, password, displayName ?? ''];
}

class LogoutRequested extends AuthEvent {
  const LogoutRequested();
}

class AuthCheckRequested extends AuthEvent {
  const AuthCheckRequested();
}

/// El interceptor no pudo renovar el token y limpió la sesión.
class AuthSessionExpired extends AuthEvent {
  const AuthSessionExpired();
}

// States
abstract class AuthState extends Equatable {
  const AuthState();
  
  @override
  List<Object> get props => [];
}

class AuthInitial extends AuthState {
  const AuthInitial();
}

class AuthLoading extends AuthState {
  const AuthLoading();
}

class AuthAuthenticated extends AuthState {
  final User user;
  
  const AuthAuthenticated({required this.user});
  
  @override
  List<Object> get props => [user];
}

class AuthUnauthenticated extends AuthState {
  const AuthUnauthenticated();
}

class AuthError extends AuthState {
  final String message;
  
  const AuthError({required this.message});
  
  @override
  List<Object> get props => [message];
}


// BLoC
class AuthBloc extends Bloc<AuthEvent, AuthState> {
  final LoginUseCase loginUseCase;
  final RegisterUseCase registerUseCase;
  final LogoutUseCase logoutUseCase;
  final RestoreSessionUseCase restoreSessionUseCase;
  StreamSubscription<void>? _sessionExpiredSubscription;
  
  AuthBloc({
    required this.loginUseCase,
    required this.registerUseCase,
    required this.logoutUseCase,
    required this.restoreSessionUseCase,
    SessionExpiredNotifier? sessionExpiredNotifier,
  }) : super(const AuthInitial()) {
    on<LoginRequested>(_onLoginRequested);
    on<RegisterRequested>(_onRegisterRequested);
    on<LogoutRequested>(_onLogoutRequested);
    on<AuthCheckRequested>(_onAuthCheckRequested);
    on<AuthSessionExpired>(_onSessionExpired);
    
    _sessionExpiredSubscription = sessionExpiredNotifier?.stream
        .listen((_) => add(const AuthSessionExpired()));
  }
  
  Future<void> _onLoginRequested(
    LoginRequested event,
    Emitter<AuthState> emit,
  ) async {
    emit(const AuthLoading());
    
    final result = await loginUseCase(
      LoginParams(email: event.email, password: event.password),
    );
    
    result.fold(
      (failure) => emit(AuthError(message: _mapFailureToMessage(failure))),
      (user) => emit(AuthAuthenticated(user: user)),
    );
  }
  
  Future<void> _onRegisterRequested(
    RegisterRequested event,
    Emitter<AuthState> emit,
  ) async {
    emit(const AuthLoading());
    
    final result = await registerUseCase(
      RegisterParams(
        username: event.username,
        email: event.email,
        password: event.password,
        displayName: event.displayName,
      ),
    );
    
    result.fold(
      (failure) => emit(AuthError(message: _mapFailureToMessage(failure))),
      (user) => emit(AuthAuthenticated(user: user)),
    );
  }
  
  Future<void> _onLogoutRequested(
    LogoutRequested event,
    Emitter<AuthState> emit,
  ) async {
    emit(const AuthLoading());
    
    await logoutUseCase(const NoParams());
    
    emit(const AuthUnauthenticated());
  }
  
  /// Auto-login: sin `GET /auth/me` (Ola 1), el usuario sale del refresh.
  Future<void> _onAuthCheckRequested(
    AuthCheckRequested event,
    Emitter<AuthState> emit,
  ) async {
    final result = await restoreSessionUseCase(const NoParams());
    
    result.fold(
      (_) => emit(const AuthUnauthenticated()),
      (user) => emit(AuthAuthenticated(user: user)),
    );
  }
  
  void _onSessionExpired(
    AuthSessionExpired event,
    Emitter<AuthState> emit,
  ) {
    if (state is! AuthUnauthenticated) emit(const AuthUnauthenticated());
  }
  
  /// `message` viene del envelope ADR-003 (es-ES) o del datasource.
  String _mapFailureToMessage(Failure failure) {
    return failure.message.isNotEmpty
        ? failure.message
        : 'Ocurrió un error inesperado';
  }
  
  @override
  Future<void> close() async {
    await _sessionExpiredSubscription?.cancel();
    return super.close();
  }
}
