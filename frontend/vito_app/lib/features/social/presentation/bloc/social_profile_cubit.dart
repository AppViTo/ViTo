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

  /// Carga el perfil. Con [silent], si ya hay uno a la vista se recarga sin
  /// volver al indicador de carga y, si falla, se queda el que había (p. ej.
  /// al volver a la pestaña Perfil).
  Future<void> load({bool silent = false}) async {
    final current = state.profile;
    final keep = silent && current != null;
    if (!keep) emit(const SocialProfileState());
    final result = await repository.getProfile(userId);
    // La página puede cerrarse con la petición en curso.
    if (isClosed) return;
    result.fold(
      (failure) {
        if (keep) return;
        emit(SocialProfileState(
          status: SocialProfileStatus.failure,
          error: failure.message,
        ));
      },
      (profile) => emit(SocialProfileState(
        status: SocialProfileStatus.loaded,
        profile: profile,
        followBusy: state.followBusy,
      )),
    );
  }

  /// Sigue o deja de seguir con actualización optimista: el botón y el
  /// contador cambian al instante y se revierten si el backend falla. Tras
  /// el `204` se recarga el perfil: follow y unfollow son idempotentes, así
  /// que el contador optimista puede no coincidir con el del backend (p. ej.
  /// si ya lo seguía desde otro dispositivo).
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
    if (isClosed) return;
    if (result.isLeft()) {
      emit(SocialProfileState(
        status: SocialProfileStatus.loaded,
        profile: before,
        error: result.fold((failure) => failure.message, (_) => null),
      ));
      return;
    }

    final fresh = await repository.getProfile(userId);
    if (isClosed) return;
    emit(SocialProfileState(
      status: SocialProfileStatus.loaded,
      // Si la recarga falla, el follow sí se hizo: queda el optimista.
      profile: fresh.fold((_) => optimistic, (profile) => profile),
    ));
  }
}
