import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../../core/di/injection_container.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../auth/presentation/bloc/auth_bloc.dart';
import '../../../social/domain/entities/user_summary.dart';
import '../../../social/domain/repositories/social_repository.dart';
import '../../../social/domain/usecases/search_users.dart';
import '../../../social/presentation/bloc/follow_list_cubit.dart';
import '../../../social/presentation/pages/profile_page.dart';
import '../../../social/presentation/widgets/user_list.dart';

enum PeopleFilter { siguiendo, seguidores }

/// "Mi Gente" (Ola 2a, T054): a quién sigues (`listFollowing`) y quién te
/// sigue (`listFollowers`) con la sesión actual, y búsqueda de personas con
/// `searchUsers`. Nunca datos de ejemplo (Bug-F06, parte social de T-F02).
class PeopleScreen extends StatefulWidget {
  /// Por defecto, el repositorio registrado en `sl`. Las pruebas pasan otro.
  final SocialRepository? repository;

  /// Cada aviso recarga las listas. La pestaña "Mi Gente" lo usa al volver a
  /// ella: sigue viva en el `IndexedStack` con los datos de la primera carga.
  final Listenable? reloadSignal;

  const PeopleScreen({super.key, this.repository, this.reloadSignal});

  @override
  State<PeopleScreen> createState() => _PeopleScreenState();
}

class _PeopleScreenState extends State<PeopleScreen> {
  late final SocialRepository _repository =
      widget.repository ?? sl<SocialRepository>();
  late final SearchUsersUseCase _searchUsers = SearchUsersUseCase(_repository);
  final _searchController = TextEditingController();
  Timer? _searchDebounce;

  PeopleFilter _selectedFilter = PeopleFilter.siguiendo;

  /// Búsqueda activa: `null` muestra las listas; una lista, sus resultados.
  List<UserSummary>? _results;
  bool _searching = false;
  String? _searchError;

  /// Sube para recargar las listas (forma parte de la clave de su cubit).
  int _listsVersion = 0;

  @override
  void initState() {
    super.initState();
    widget.reloadSignal?.addListener(_reloadLists);
  }

  @override
  void didUpdateWidget(covariant PeopleScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.reloadSignal != widget.reloadSignal) {
      oldWidget.reloadSignal?.removeListener(_reloadLists);
      widget.reloadSignal?.addListener(_reloadLists);
    }
  }

  @override
  void dispose() {
    widget.reloadSignal?.removeListener(_reloadLists);
    _searchDebounce?.cancel();
    _searchController.dispose();
    super.dispose();
  }

  void _reloadLists() {
    if (mounted) setState(() => _listsVersion++);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.grisCarbon,
      body: SafeArea(
        child: BlocBuilder<AuthBloc, AuthState>(
          builder: (context, authState) {
            if (authState is! AuthAuthenticated) {
              return const Center(
                child: CircularProgressIndicator(color: AppColors.coralNeon),
              );
            }
            final user = authState.user;
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildHeader(user.profile?.displayName ?? user.username),
                _buildSearchBar(),
                if (_results == null && !_searching && _searchError == null)
                  _buildFilterBar(),
                Expanded(child: _buildContent(user.id)),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _buildHeader(String userName) {
    return Container(
      padding: const EdgeInsets.fromLTRB(24, 20, 24, 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          const Text(
            'Tu Gente',
            style: TextStyle(
              fontSize: 28,
              fontWeight: FontWeight.w800,
              color: AppColors.blancoHielo,
            ),
          ),
          Flexible(
            child: Text(
              userName,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w500,
                color: AppColors.coralNeon,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSearchBar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 8, 24, 12),
      child: Container(
        height: 44,
        decoration: BoxDecoration(
          color: AppColors.surfaceDark,
          borderRadius: BorderRadius.circular(12),
        ),
        child: TextField(
          controller: _searchController,
          onChanged: _onSearchChanged,
          decoration: const InputDecoration(
            hintText: 'Buscar por nombre o @usuario',
            hintStyle: TextStyle(color: AppColors.textHint, fontSize: 14),
            prefixIcon:
                Icon(Icons.search_rounded, size: 20, color: AppColors.textHint),
            border: InputBorder.none,
            contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          ),
          style: const TextStyle(color: AppColors.blancoHielo, fontSize: 14),
        ),
      ),
    );
  }

  /// Desde 2 caracteres (sin contar una `@`), con 300 ms entre teclas.
  void _onSearchChanged(String query) {
    _searchDebounce?.cancel();
    if (normalizeSearchQuery(query).length < minSearchQueryLength) {
      setState(() {
        _results = null;
        _searching = false;
        _searchError = null;
      });
      return;
    }
    _searchDebounce = Timer(const Duration(milliseconds: 300), () async {
      setState(() => _searching = true);
      final result = await _searchUsers(query);
      if (!mounted || _searchController.text != query) return;
      result.fold(
        (failure) => setState(() {
          _searching = false;
          _results = null;
          _searchError = failure.message;
        }),
        (users) => setState(() {
          _searching = false;
          _searchError = null;
          _results = users;
        }),
      );
    });
  }

  Widget _buildFilterBar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 0, 24, 12),
      child: Row(
        children: [
          Expanded(child: _buildFilterChip('Siguiendo', PeopleFilter.siguiendo)),
          const SizedBox(width: 8),
          Expanded(child: _buildFilterChip('Seguidores', PeopleFilter.seguidores)),
        ],
      ),
    );
  }

  Widget _buildFilterChip(String label, PeopleFilter filter) {
    final isSelected = _selectedFilter == filter;
    return GestureDetector(
      onTap: () => setState(() => _selectedFilter = filter),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: BoxDecoration(
          color: isSelected ? AppColors.coralNeon : AppColors.surfaceDark,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: isSelected ? AppColors.coralNeon : AppColors.grey700,
            width: 1,
          ),
        ),
        child: Center(
          child: Text(
            label,
            style: TextStyle(
              fontSize: 13,
              fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
              color: isSelected ? AppColors.blancoHielo : AppColors.textSecondary,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildContent(String myId) {
    if (_searching) {
      return const Center(
        child: CircularProgressIndicator(color: AppColors.coralNeon),
      );
    }
    final error = _searchError;
    if (error != null) return _centered(error);
    final results = _results;
    if (results != null) {
      if (results.isEmpty) {
        return _centered('No encontramos a nadie con ese nombre o usuario');
      }
      return ListView(
        children: [
          for (final user in results)
            UserTile(user: user, onTap: () => _openProfile(user)),
        ],
      );
    }

    final following = _selectedFilter == PeopleFilter.siguiendo;
    return BlocProvider(
      key: ValueKey('$myId-$_selectedFilter-$_listsVersion'),
      create: (_) => FollowListCubit(
        userId: myId,
        kind: following ? FollowListKind.following : FollowListKind.followers,
        repository: _repository,
      )..load(),
      child: FollowListView(
        emptyMessage: following ? 'Aún no sigues a nadie' : 'Aún nadie te sigue',
        onUserTap: _openProfile,
      ),
    );
  }

  /// Abre el perfil y, al volver, recarga las listas: desde el perfil se
  /// pudo seguir o dejar de seguir.
  Future<void> _openProfile(UserSummary user) async {
    await Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => ProfilePage(userId: user.userId, repository: _repository),
    ));
    _reloadLists();
  }

  Widget _centered(String message) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 32),
        child: Text(
          message,
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: 14,
            color: AppColors.textSecondary.withValues(alpha: 0.7),
          ),
        ),
      ),
    );
  }
}
