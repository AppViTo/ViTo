import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:intl/intl.dart';

import '../bloc/mosaic_bloc.dart';
import '../../domain/entities/mosaic.dart';

class MosaicFeedWidget extends StatelessWidget {
  final String eventId;
  
  const MosaicFeedWidget({super.key, required this.eventId});
  
  @override
  Widget build(BuildContext context) {
    return BlocBuilder<MosaicBloc, MosaicState>(
      builder: (context, state) {
        if (state is MosaicLoading) {
          return const Center(child: CircularProgressIndicator());
        }
        
        if (state is MosaicFeedLoaded) {
          return _buildFeed(context, state.feed);
        }
        
        if (state is MosaicRealTimeUpdate) {
          // Reload feed when new item arrives
          context.read<MosaicBloc>().add(LoadEventFeed(eventId));
          return const Center(child: CircularProgressIndicator());
        }
        
        return _buildEmptyFeed(context);
      },
    );
  }
  
  Widget _buildFeed(BuildContext context, List<MosaicFeedItem> feed) {
    if (feed.isEmpty) {
      return _buildEmptyFeed(context);
    }
    
    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: feed.length,
      itemBuilder: (context, index) {
        final item = feed[index];
        return _buildFeedItem(context, item);
      },
    );
  }
  
  Widget _buildFeedItem(BuildContext context, MosaicFeedItem item) {
    return Card(
      margin: const EdgeInsets.only(bottom: 16),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header
            Row(
              children: [
                CircleAvatar(
                  radius: 20,
                  backgroundImage: item.avatar != null 
                      ? NetworkImage(item.avatar!) 
                      : null,
                  child: item.avatar == null 
                      ? Text(item.displayName[0].toUpperCase())
                      : null,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        item.displayName,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                      Text(
                        '@${item.username}',
                        style: TextStyle(
                          color: Colors.grey[600],
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),
                _buildTypeBadge(item.type),
              ],
            ),
            
            // Content
            if (item.content != null && item.content!.isNotEmpty) ...[
              const SizedBox(height: 12),
              Text(
                item.content!,
                style: const TextStyle(fontSize: 14),
              ),
            ],
            
            // Media
            if (item.mediaUrls.isNotEmpty) ...[
              const SizedBox(height: 12),
              _buildMediaGrid(item.mediaUrls),
            ],
            
            // Actions
            const SizedBox(height: 12),
            Row(
              children: [
                // Like button
                InkWell(
                  onTap: () {
                    if (item.isLiked) {
                      context.read<MosaicBloc>().add(UnlikePost(item.id));
                    } else {
                      context.read<MosaicBloc>().add(LikePost(item.id));
                    }
                  },
                  child: Row(
                    children: [
                      Icon(
                        item.isLiked ? Icons.favorite : Icons.favorite_border,
                        color: item.isLiked ? Colors.red : Colors.grey,
                        size: 20,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        item.likesCount.toString(),
                        style: TextStyle(
                          color: item.isLiked ? Colors.red : Colors.grey,
                        ),
                      ),
                    ],
                  ),
                ),
                
                const SizedBox(width: 16),
                
                // Comments button
                Row(
                  children: [
                    Icon(
                      Icons.chat_bubble_outline,
                      color: Colors.grey,
                      size: 20,
                    ),
                    const SizedBox(width: 4),
                    Text(
                      item.commentsCount.toString(),
                      style: TextStyle(color: Colors.grey),
                    ),
                  ],
                ),
                
                const Spacer(),
                
                // Timestamp
                Text(
                  _formatTimestamp(item.timestamp),
                  style: TextStyle(
                    color: Colors.grey[500],
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
  
  Widget _buildTypeBadge(FeedItemType type) {
    Color color;
    String label;
    IconData icon;
    
    switch (type) {
      case FeedItemType.checkIn:
        color = Colors.green;
        label = 'Check-in';
        icon = Icons.check_circle;
        break;
      case FeedItemType.photo:
        color = Colors.blue;
        label = 'Photo';
        icon = Icons.photo;
        break;
      case FeedItemType.liveUpdate:
        color = Colors.red;
        label = 'Live';
        icon = Icons.live_tv;
        break;
      case FeedItemType.announcement:
        color = Colors.orange;
        label = 'Announcement';
        icon = Icons.campaign;
        break;
      default:
        color = Colors.grey;
        label = 'Post';
        icon = Icons.post_add;
    }
    
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withOpacity(0.3)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: color, size: 12),
          const SizedBox(width: 4),
          Text(
            label,
            style: TextStyle(
              color: color,
              fontSize: 10,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }
  
  Widget _buildMediaGrid(List<String> mediaUrls) {
    if (mediaUrls.length == 1) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: Image.network(
          mediaUrls.first,
          fit: BoxFit.cover,
          width: double.infinity,
          height: 200,
        ),
      );
    }
    
    return GridView.builder(
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        crossAxisSpacing: 4,
        mainAxisSpacing: 4,
      ),
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: mediaUrls.length.clamp(0, 4),
      itemBuilder: (context, index) {
        return ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: Stack(
            fit: StackFit.expand,
            children: [
              Image.network(
                mediaUrls[index],
                fit: BoxFit.cover,
              ),
              if (index == 3 && mediaUrls.length > 4)
                Container(
                  color: Colors.black54,
                  child: Center(
                    child: Text(
                      '+${mediaUrls.length - 4}',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 24,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ),
            ],
          ),
        );
      },
    );
  }
  
  Widget _buildEmptyFeed(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.feed,
            size: 64,
            color: Colors.grey[400],
          ),
          const SizedBox(height: 16),
          Text(
            'No posts yet',
            style: TextStyle(
              fontSize: 18,
              color: Colors.grey[600],
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Be the first to share something!',
            style: TextStyle(
              color: Colors.grey[500],
            ),
          ),
        ],
      ),
    );
  }
  
  String _formatTimestamp(DateTime? timestamp) {
    if (timestamp == null) return '';
    
    final now = DateTime.now();
    final difference = now.difference(timestamp);
    
    if (difference.inMinutes < 1) {
      return 'Just now';
    } else if (difference.inHours < 1) {
      return '${difference.inMinutes}m ago';
    } else if (difference.inDays < 1) {
      return '${difference.inHours}h ago';
    } else {
      return DateFormat('MMM d, y').format(timestamp);
    }
  }
}
