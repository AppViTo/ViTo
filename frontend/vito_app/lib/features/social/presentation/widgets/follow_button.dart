import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../bloc/social_bloc.dart';

class FollowButton extends StatelessWidget {
  final String userId;
  final bool isFollowing;
  
  const FollowButton({
    super.key,
    required this.userId,
    required this.isFollowing,
  });
  
  @override
  Widget build(BuildContext context) {
    return BlocBuilder<SocialBloc, SocialState>(
      builder: (context, state) {
        final isLoading = state is SocialLoading;
        
        return SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            onPressed: isLoading
                ? null
                : () {
                    if (isFollowing) {
                      context.read<SocialBloc>().add(UnfollowUser(userId));
                    } else {
                      context.read<SocialBloc>().add(FollowUser(userId));
                    }
                  },
            style: ElevatedButton.styleFrom(
              backgroundColor: isFollowing ? Colors.grey : Colors.blue,
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(vertical: 12),
            ),
            child: isLoading
                ? const SizedBox(
                    height: 20,
                    width: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : Text(
                    isFollowing ? 'Unfollow' : 'Follow',
                    style: const TextStyle(fontSize: 16),
                  ),
          ),
        );
      },
    );
  }
}
