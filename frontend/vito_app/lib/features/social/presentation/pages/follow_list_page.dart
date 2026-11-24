import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../../core/di/injection_container.dart';
import '../../../../core/theme/app_colors.dart';
import '../../domain/repositories/social_repository.dart';
import '../bloc/follow_list_cubit.dart';
import '../widgets/user_list.dart';
import 'profile_page.dart';

/// Seguidores o seguidos de un usuario (T053), desde su perfil. Tocar a
/// alguien abre su perfil.
class FollowListPage extends StatelessWidget {
  final String userId;
  final FollowListKind kind;

  /// Por defecto, el repositorio registrado en `sl`. Las pruebas pasan otro.
  final SocialRepository? repository;

  const FollowListPage({
    super.key,
    required this.userId,
    required this.kind,
    this.repository,
  });

  @override
  Widget build(BuildContext context) {
    final repo = repository ?? sl<SocialRepository>();
    final followers = kind == FollowListKind.followers;
    return Scaffold(
      backgroundColor: AppColors.grisCarbon,
      appBar: AppBar(
        backgroundColor: AppColors.grisCarbon,
        title: Text(followers ? 'Seguidores' : 'Siguiendo'),
      ),
      body: BlocProvider(
        create: (_) =>
            FollowListCubit(userId: userId, kind: kind, repository: repo)..load(),
        child: FollowListView(
          emptyMessage: followers ? 'Aún no tiene seguidores' : 'Aún no sigue a nadie',
          onUserTap: (user) => Navigator.of(context).push(MaterialPageRoute(
            builder: (_) => ProfilePage(userId: user.userId, repository: repo),
          )),
        ),
      ),
    );
  }
}
