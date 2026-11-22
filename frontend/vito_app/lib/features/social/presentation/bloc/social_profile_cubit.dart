import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../domain/entities/social_profile.dart';
import '../../domain/repositories/social_repository.dart';

enum SocialProfileStatus { loading, loaded, failure }

/// Perfil social de un usuario (T048) y su botón de seguir (T042).
class SocialProfileState extends Equatable {
  final SocialProfileStatus status;
  final SocialProfile? profile;

  /// Hay un follow o unfollow en curso.
  final bool followBusy;

  /// Error de carga, o de la última acción para mostrarlo una vez.
  final String? error;

  const SocialProfileState({
    this.status = SocialProfileStatus.loading,
    this.profile,
    this.followBusy = false,
    this.error,
  });

  @override
  List<Object?> get props => [status, profile, followBusy, error];
}

class SocialProfileCubit extends Cubit<SocialProfileState> {
  final String userId;
  final SocialRepository repository;

  SocialProfileCubit({required this.userId, required this.repository})
      : super(const SocialProfileState());

  Future<void> load() async {
    emit(const SocialProfileState());
    final result = await repository.getProfile(userId);
    result.fold(
      (failure) => emit(SocialProfileState(
        status: SocialProfileStatus.failure,
        error: failure.message,
      )),
      (profile) => emit(SocialProfileState(
        status: SocialProfileStatus.loaded,
        profile: profile,
      )),
    );
  }

  /// Sigue o deja de seguir con actualización optimista: el botón y el
  /// contador cambian al instante y se revierten si el backend falla. Follow
  /// y unfollow son idempotentes (`204`), así que repetirlos no duplica nada.
  Future<void> toggleFollow() async {
    final before = state.profile;
    if (before == null || state.followBusy) return;

    final follow = !before.isFollowing;
    final optimistic = before.copyWith(
      isFollowing: follow,
      followersCount: before.followersCount + (follow ? 1 : -1),
    );
    emit(SocialProfileState(
      status: SocialProfileStatus.loaded,
      profile: optimistic,
      followBusy: true,
    ));

    final result = follow
        ? await repository.follow(userId)
        : await repository.unfollow(userId);
    result.fold(
      (failure) => emit(SocialProfileState(
        status: SocialProfileStatus.loaded,
        profile: before,
        error: failure.message,
      )),
      (_) => emit(SocialProfileState(
        status: SocialProfileStatus.loaded,
        profile: optimistic,
      )),
    );
  }
}
