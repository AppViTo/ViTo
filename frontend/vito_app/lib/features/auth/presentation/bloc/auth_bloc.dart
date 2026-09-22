import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:equatable/equatable.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../../domain/entities/user.dart';
import '../../domain/repositories/auth_repository.dart';
import '../../domain/usecases/login.dart';
import '../../domain/usecases/register.dart';
import '../../domain/usecases/logout.dart';

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
  final AuthRepository authRepository;
  
  AuthBloc({
    required this.loginUseCase,
    required this.registerUseCase,
    required this.logoutUseCase,
    required this.authRepository,
  }) : super(const AuthInitial()) {
    on<LoginRequested>(_onLoginRequested);
    on<RegisterRequested>(_onRegisterRequested);
    on<LogoutRequested>(_onLogoutRequested);
    on<AuthCheckRequested>(_onAuthCheckRequested);
  }
  
  Future<void> _onLoginRequested(
    LoginRequested event,
    Emitter<AuthState> emit,
  ) async {
    emit(const AuthLoading());
    
    // MOCK: Simular login exitoso sin backend
    await Future.delayed(const Duration(milliseconds: 800));
    
    final testUser = User(
      id: 'usr_test_001',
      username: 'alex_villa',
      email: event.email,
      profile: const UserProfile(
        displayName: 'Alex Villa',
        bio: 'Amante de las fiestas y los buenos momentos 🎉',
        avatar: null,
        coverImage: null,
        location: UserLocation(
          type: 'Point',
          coordinates: [-74.006, 4.609],
          city: 'Bogotá',
          country: 'Colombia',
        ),
      ),
      stats: const UserStats(
        followersCount: 128,
        followingCount: 95,
        postsCount: 42,
        streamsCount: 7,
      ),
      roles: const ['user'],
      createdAt: DateTime(2024, 1, 15),
    );
    
    emit(AuthAuthenticated(user: testUser));
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
  
  Future<void> _onAuthCheckRequested(
    AuthCheckRequested event,
    Emitter<AuthState> emit,
  ) async {
    // MOCK: Simular usuario ya logueado
    final testUser = User(
      id: 'usr_test_001',
      username: 'alex_villa',
      email: 'alex@vito.com',
      profile: const UserProfile(
        displayName: 'Alex Villa',
        bio: 'Amante de las fiestas y los buenos momentos',
        avatar: null,
        coverImage: null,
        location: UserLocation(
          type: 'Point',
          coordinates: [-74.006, 4.609],
          city: 'Bogotá',
          country: 'Colombia',
        ),
      ),
      stats: const UserStats(
        followersCount: 128,
        followingCount: 95,
        postsCount: 42,
        streamsCount: 7,
      ),
      roles: const ['user'],
      createdAt: DateTime(2024, 1, 15),
    );
    emit(AuthAuthenticated(user: testUser));
  }
  
  String _mapFailureToMessage(Failure failure) {
    if (failure is ServerFailure) {
      return failure.message;
    } else if (failure is AuthenticationFailure) {
      return failure.message;
    } else if (failure is NetworkFailure) {
      return failure.message;
    } else {
      return 'Unexpected error occurred';
    }
  }
}
