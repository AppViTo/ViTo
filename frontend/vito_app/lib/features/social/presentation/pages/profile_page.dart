import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:image_picker/image_picker.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../auth/presentation/bloc/auth_bloc.dart';

class ProfilePage extends StatefulWidget {
  final String? userId;

  const ProfilePage({super.key, this.userId});

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  String? _coverImagePath;
  final ImagePicker _picker = ImagePicker();

  Future<void> _pickCoverImage() async {
    final XFile? image = await _picker.pickImage(
      source: ImageSource.gallery,
      maxWidth: 1920,
      maxHeight: 1080,
      imageQuality: 85,
    );

    if (image != null) {
      setState(() {
        _coverImagePath = image.path;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.grisCarbon,
      body: BlocBuilder<AuthBloc, AuthState>(
        builder: (context, authState) {
          if (authState is! AuthAuthenticated) {
            return const Center(
              child: CircularProgressIndicator(color: AppColors.coralNeon),
            );
          }

          final user = authState.user;
          final displayName = user.profile?.displayName ?? user.username;
          final bio = user.profile?.bio;
          final avatar = user.profile?.avatar;
          final followersCount = user.stats?.followersCount ?? 0;
          final followingCount = user.stats?.followingCount ?? 0;
          final postsCount = user.stats?.postsCount ?? 0;
          final streamsCount = user.stats?.streamsCount ?? 0;
          final city = user.profile?.location?.city;
          final country = user.profile?.location?.country;

          return CustomScrollView(
            slivers: [
              SliverAppBar(
                expandedHeight: 220,
                pinned: true,
                backgroundColor: AppColors.grisCarbon,
                flexibleSpace: FlexibleSpaceBar(
                  background: GestureDetector(
                    onTap: widget.userId == null ? _pickCoverImage : null,
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        // Cover image or coral gradient
                        if (_coverImagePath != null)
                          kIsWeb
                              ? Image.network(_coverImagePath!, fit: BoxFit.cover)
                              : Image.file(File(_coverImagePath!), fit: BoxFit.cover)
                        else
                          Container(
                            decoration: const BoxDecoration(
                              gradient: LinearGradient(
                                begin: Alignment.topCenter,
                                end: Alignment.bottomCenter,
                                colors: [AppColors.coralNeon, AppColors.grisCarbon],
                              ),
                            ),
                          ),

                        // Dark overlay at bottom for readability
                        Positioned(
                          bottom: 0,
                          left: 0,
                          right: 0,
                          height: 80,
                          child: Container(
                            decoration: const BoxDecoration(
                              gradient: LinearGradient(
                                begin: Alignment.topCenter,
                                end: Alignment.bottomCenter,
                                colors: [Colors.transparent, AppColors.grisCarbon],
                              ),
                            ),
                          ),
                        ),

                        // Edit button (only for own profile)
                        if (widget.userId == null)
                          Positioned(
                            bottom: 24,
                            right: 20,
                            child: GestureDetector(
                              onTap: _pickCoverImage,
                              child: Container(
                                padding: const EdgeInsets.all(10),
                                decoration: BoxDecoration(
                                  color: Colors.black.withValues(alpha: 0.5),
                                  shape: BoxShape.circle,
                                ),
                                child: const Icon(
                                  Icons.camera_alt,
                                  color: AppColors.blancoHielo,
                                  size: 20,
                                ),
                              ),
                            ),
                          ),
                        // Settings button (only for own profile)
                        if (widget.userId == null)
                          Positioned(
                            top: 48,
                            right: 20,
                            child: GestureDetector(
                              onTap: () {},
                              child: Container(
                                padding: const EdgeInsets.all(10),
                                decoration: BoxDecoration(
                                  color: Colors.black.withValues(alpha: 0.5),
                                  shape: BoxShape.circle,
                                ),
                                child: const Icon(
                                  Icons.settings,
                                  color: AppColors.blancoHielo,
                                  size: 20,
                                ),
                              ),
                            ),
                          ),

                      ],
                    ),
                  ),
                ),
              ),
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 24),
                  child: Column(
                    children: [
                      const SizedBox(height: 24),

                      // Avatar
                      CircleAvatar(
                        radius: 48,
                        backgroundColor: AppColors.lavandaPale.withValues(alpha: 0.2),
                        backgroundImage: avatar != null ? NetworkImage(avatar) : null,
                        child: avatar == null
                            ? Text(
                                displayName[0].toUpperCase(),
                                style: const TextStyle(
                                  fontSize: 36,
                                  fontWeight: FontWeight.w700,
                                  color: AppColors.blancoHielo,
                                ),
                              )
                            : null,
                      ),
                      const SizedBox(height: 16),

                      // Name
                      Text(
                        displayName,
                        style: const TextStyle(
                          fontSize: 24,
                          fontWeight: FontWeight.w800,
                          color: AppColors.blancoHielo,
                        ),
                      ),
                      const SizedBox(height: 4),

                      // Username
                      Text(
                        '@${user.username}',
                        style: const TextStyle(
                          fontSize: 15,
                          color: AppColors.textSecondary,
                        ),
                      ),

                      // Location
                      if (city != null) ...[
                        const SizedBox(height: 8),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Icon(Icons.location_on_outlined, size: 16, color: AppColors.textSecondary),
                            const SizedBox(width: 4),
                            Text(
                              '$city${country != null ? ', $country' : ''}',
                              style: const TextStyle(fontSize: 13, color: AppColors.textSecondary),
                            ),
                          ],
                        ),
                      ],

                      // Bio
                      if (bio != null && bio.isNotEmpty) ...[
                        const SizedBox(height: 12),
                        Text(
                          bio,
                          style: const TextStyle(
                            fontSize: 14,
                            color: AppColors.textSecondary,
                            height: 1.4,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ],

                      const SizedBox(height: 24),

                      // Stats Row
                      Container(
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceDark,
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                          children: [
                            _buildStat(context, '$followersCount', 'Seguidores'),
                            Container(width: 1, height: 32, color: AppColors.grey700),
                            _buildStat(context, '$followingCount', 'Siguiendo'),
                            Container(width: 1, height: 32, color: AppColors.grey700),
                            _buildStat(context, '$postsCount', 'Posts'),
                            Container(width: 1, height: 32, color: AppColors.grey700),
                            _buildStat(context, '$streamsCount', 'Streams'),
                          ],
                        ),
                      ),

                      const SizedBox(height: 24),

                      // Action Buttons
                      if (widget.userId == null) ...[
                        // Edit Profile
                        SizedBox(
                          width: double.infinity,
                          child: OutlinedButton.icon(
                            onPressed: () {},
                            icon: const Icon(Icons.edit_outlined, size: 18),
                            label: const Text('Editar Perfil'),
                            style: OutlinedButton.styleFrom(
                              foregroundColor: AppColors.blancoHielo,
                              side: const BorderSide(color: AppColors.grey700),
                              padding: const EdgeInsets.symmetric(vertical: 14),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(height: 12),

                        // Logout
                        SizedBox(
                          width: double.infinity,
                          child: OutlinedButton.icon(
                            onPressed: () {
                              context.read<AuthBloc>().add(const LogoutRequested());
                            },
                            icon: const Icon(Icons.logout, size: 18, color: AppColors.coralNeon),
                            label: const Text(
                              'Cerrar Sesión',
                              style: TextStyle(color: AppColors.coralNeon),
                            ),
                            style: OutlinedButton.styleFrom(
                              side: const BorderSide(color: AppColors.coralNeon),
                              padding: const EdgeInsets.symmetric(vertical: 14),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                            ),
                          ),
                        ),
                      ] else ...[
                        // Follow Button for other users
                        SizedBox(
                          width: double.infinity,
                          child: ElevatedButton.icon(
                            onPressed: () {},
                            icon: const Icon(Icons.person_add_outlined, size: 18),
                            label: const Text('Seguir'),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: AppColors.coralNeon,
                              foregroundColor: AppColors.blancoHielo,
                              padding: const EdgeInsets.symmetric(vertical: 14),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                            ),
                          ),
                        ),
                      ],

                      const SizedBox(height: 24),
                    ],
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  Widget _buildStat(BuildContext context, String value, String label) {
    return Column(
      children: [
        Text(
          value,
          style: const TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.w700,
            color: AppColors.blancoHielo,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          label,
          style: const TextStyle(
            fontSize: 12,
            color: AppColors.textSecondary,
          ),
        ),
      ],
    );
  }
}
