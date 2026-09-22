import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:equatable/equatable.dart';

import '../../domain/entities/social_profile.dart';
import '../../domain/usecases/follow.dart';
import '../../domain/usecases/unfollow.dart';
import '../../domain/usecases/get_profile.dart';
import '../../domain/repositories/social_repository.dart';

// Events
abstract class SocialEvent extends Equatable {
  const SocialEvent();
  
  @override
  List<Object?> get props => [];
}

class FollowUser extends SocialEvent {
  final String userId;
  
  const FollowUser(this.userId);
  
  @override
  List<Object?> get props => [userId];
}

class UnfollowUser extends SocialEvent {
  final String userId;
  
  const UnfollowUser(this.userId);
  
  @override
  List<Object?> get props => [userId];
}

class LoadProfile extends SocialEvent {
  final String userId;
  
  const LoadProfile(this.userId);
  
  @override
  List<Object?> get props => [userId];
}

class LoadFollowers extends SocialEvent {
  final String userId;
  
  const LoadFollowers(this.userId);
  
  @override
  List<Object?> get props => [userId];
}

class LoadFollowing extends SocialEvent {
  final String userId;
  
  const LoadFollowing(this.userId);
  
  @override
  List<Object?> get props => [userId];
}

class SearchUsers extends SocialEvent {
  final String query;
  
  const SearchUsers(this.query);
  
  @override
  List<Object?> get props => [query];
}

// States
abstract class SocialState extends Equatable {
  const SocialState();
  
  @override
  List<Object?> get props => [];
}

class SocialInitial extends SocialState {}

class SocialLoading extends SocialState {}

class SocialLoaded extends SocialState {
  final SocialProfile profile;
  
  const SocialLoaded(this.profile);
  
  @override
  List<Object?> get props => [profile];
}

class FollowersLoaded extends SocialState {
  final List<SocialProfile> followers;
  
  const FollowersLoaded(this.followers);
  
  @override
  List<Object?> get props => [followers];
}

class FollowingLoaded extends SocialState {
  final List<SocialProfile> following;
  
  const FollowingLoaded(this.following);
  
  @override
  List<Object?> get props => [following];
}

class SearchResultsLoaded extends SocialState {
  final List<SocialProfile> users;
  
  const SearchResultsLoaded(this.users);
  
  @override
  List<Object?> get props => [users];
}

class SocialError extends SocialState {
  final String message;
  
  const SocialError(this.message);
  
  @override
  List<Object?> get props => [message];
}

// BLoC
class SocialBloc extends Bloc<SocialEvent, SocialState> {
  final FollowUseCase followUseCase;
  final UnfollowUseCase unfollowUseCase;
  final GetProfileUseCase getProfileUseCase;
  final SocialRepository socialRepository;
  
  SocialBloc({
    required this.followUseCase,
    required this.unfollowUseCase,
    required this.getProfileUseCase,
    required this.socialRepository,
  }) : super(SocialInitial()) {
    on<FollowUser>(_onFollowUser);
    on<UnfollowUser>(_onUnfollowUser);
    on<LoadProfile>(_onLoadProfile);
    on<LoadFollowers>(_onLoadFollowers);
    on<LoadFollowing>(_onLoadFollowing);
    on<SearchUsers>(_onSearchUsers);
  }
  
  Future<void> _onFollowUser(FollowUser event, Emitter<SocialState> emit) async {
    emit(SocialLoading());
    
    final result = await followUseCase(event.userId);
    
    result.fold(
      (failure) => emit(SocialError(failure.message)),
      (_) => add(LoadProfile(event.userId)),
    );
  }
  
  Future<void> _onUnfollowUser(UnfollowUser event, Emitter<SocialState> emit) async {
    emit(SocialLoading());
    
    final result = await unfollowUseCase(event.userId);
    
    result.fold(
      (failure) => emit(SocialError(failure.message)),
      (_) => add(LoadProfile(event.userId)),
    );
  }
  
  Future<void> _onLoadProfile(LoadProfile event, Emitter<SocialState> emit) async {
    emit(SocialLoading());
    
    final result = await getProfileUseCase(event.userId);
    
    result.fold(
      (failure) => emit(SocialError(failure.message)),
      (profile) => emit(SocialLoaded(profile)),
    );
  }
  
  Future<void> _onLoadFollowers(LoadFollowers event, Emitter<SocialState> emit) async {
    emit(SocialLoading());
    
    final result = await socialRepository.getFollowers(userId: event.userId);
    
    result.fold(
      (failure) => emit(SocialError(failure.message)),
      (followers) => emit(FollowersLoaded(followers)),
    );
  }
  
  Future<void> _onLoadFollowing(LoadFollowing event, Emitter<SocialState> emit) async {
    emit(SocialLoading());
    
    final result = await socialRepository.getFollowing(userId: event.userId);
    
    result.fold(
      (failure) => emit(SocialError(failure.message)),
      (following) => emit(FollowingLoaded(following)),
    );
  }
  
  Future<void> _onSearchUsers(SearchUsers event, Emitter<SocialState> emit) async {
    emit(SocialLoading());
    
    final result = await socialRepository.searchUsers(query: event.query);
    
    result.fold(
      (failure) => emit(SocialError(failure.message)),
      (users) => emit(SearchResultsLoaded(users)),
    );
  }
}
