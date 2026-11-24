import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../domain/entities/user_summary.dart';
import '../../domain/repositories/social_repository.dart';

enum FollowListKind { followers, following }

/// Una lista del grafo (T053): quién sigue a [FollowListCubit.userId] o a
/// quién sigue, del seguimiento más reciente al más antiguo.
class FollowListState extends Equatable {
  final List<UserSummary> items;

  /// Primera página en curso.
  final bool loading;

  /// Página siguiente en curso.
  final bool loadingMore;

  /// Ya llegó una página vacía: no hay más.
  final bool reachedEnd;

  final String? error;

  const FollowListState({
    this.items = const [],
    this.loading = true,
    this.loadingMore = false,
    this.reachedEnd = false,
    this.error,
  });

  FollowListState copyWith({
    List<UserSummary>? items,
    bool? loading,
    bool? loadingMore,
    bool? reachedEnd,
    String? error,
  }) =>
      FollowListState(
        items: items ?? this.items,
        loading: loading ?? this.loading,
        loadingMore: loadingMore ?? this.loadingMore,
        reachedEnd: reachedEnd ?? this.reachedEnd,
        error: error,
      );

  @override
  List<Object?> get props => [items, loading, loadingMore, reachedEnd, error];
}

/// Paginación infinita: pide páginas de [pageSize] hasta recibir una vacía.
/// Una página puede traer menos de [pageSize] (el backend omite a quien
/// `auth-service` ya no devuelve), así que una página corta no es el final.
class FollowListCubit extends Cubit<FollowListState> {
  static const pageSize = 20;

  final String userId;
  final FollowListKind kind;
  final SocialRepository repository;
  int _nextPage = 0;

  /// Sube con cada [load]: las respuestas de una carga anterior se ignoran.
  int _generation = 0;

  FollowListCubit({
    required this.userId,
    required this.kind,
    required this.repository,
  }) : super(const FollowListState());

  Future<void> load() async {
    final generation = ++_generation;
    _nextPage = 0;
    emit(const FollowListState());
    await _fetch(generation);
  }

  /// Se puede pedir la página siguiente: la primera ya llegó, no hay otra en
  /// curso, no se llegó al final y la lista no está vacía por un error.
  bool get canLoadMore =>
      !isClosed &&
      !state.loading &&
      !state.loadingMore &&
      !state.reachedEnd &&
      !(state.error != null && state.items.isEmpty);

  /// Página siguiente. Tras un error la pide solo el botón "Reintentar"
  /// de la lista, no el scroll.
  Future<void> loadMore() async {
    if (!canLoadMore) return;
    emit(state.copyWith(loadingMore: true));
    await _fetch(_generation);
  }

  Future<void> _fetch(int generation) async {
    final page = _nextPage;
    final result = kind == FollowListKind.followers
        ? await repository.getFollowers(userId: userId, page: page, size: pageSize)
        : await repository.getFollowing(userId: userId, page: page, size: pageSize);
    // La página puede cerrarse (o recargarse) con la petición en curso.
    if (isClosed || generation != _generation) return;
    result.fold(
      (failure) => emit(state.copyWith(
        loading: false,
        loadingMore: false,
        error: failure.message,
      )),
      (users) {
        _nextPage = page + 1;
        final seen = state.items.map((u) => u.userId).toSet();
        emit(state.copyWith(
          items: [...state.items, ...users.where((u) => seen.add(u.userId))],
          loading: false,
          loadingMore: false,
          reachedEnd: users.isEmpty,
        ));
      },
    );
  }
}
