import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../bloc/archive_bloc.dart';
import '../bloc/archive_event.dart';
import '../bloc/archive_state.dart';
import '../../domain/entities/archive.dart';
import '../../../../core/di/injection_container.dart';

class ArchiveMediaGridWidget extends StatefulWidget {
  final String archiveId;
  final Function(ArchiveMedia)? onMediaTap;
  
  const ArchiveMediaGridWidget({
    super.key,
    required this.archiveId,
    this.onMediaTap,
  });
  
  @override
  State<ArchiveMediaGridWidget> createState() => _ArchiveMediaGridWidgetState();
}

class _ArchiveMediaGridWidgetState extends State<ArchiveMediaGridWidget> {
  late ArchiveBloc _archiveBloc;
  
  @override
  void initState() {
    super.initState();
    _archiveBloc = sl<ArchiveBloc>();
    _archiveBloc.add(LoadMedia(archiveId: widget.archiveId));
  }
  
  @override
  Widget build(BuildContext context) {
    return BlocBuilder<ArchiveBloc, ArchiveState>(
      bloc: _archiveBloc,
      builder: (context, state) {
        if (state is ArchiveLoading) {
          return const Center(
            child: CircularProgressIndicator(),
          );
        }
        
        if (state is MediaLoaded) {
          if (state.media.isEmpty) {
            return const Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.photo_library, size: 64, color: Colors.grey),
                  SizedBox(height: 16),
                  Text(
                    'No media yet',
                    style: TextStyle(color: Colors.grey),
                  ),
                ],
              ),
            );
          }
          
          return GridView.builder(
            padding: const EdgeInsets.all(8),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 3,
              crossAxisSpacing: 4,
              mainAxisSpacing: 4,
            ),
            itemCount: state.media.length,
            itemBuilder: (context, index) {
              final media = state.media[index];
              return _buildMediaItem(media);
            },
          );
        }
        
        return const SizedBox.shrink();
      },
    );
  }
  
  Widget _buildMediaItem(ArchiveMedia media) {
    return GestureDetector(
      onTap: () => widget.onMediaTap?.call(media),
      child: Stack(
        fit: StackFit.expand,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: Image.network(
              media.thumbnailUrl ?? media.url,
              fit: BoxFit.cover,
              errorBuilder: (context, error, stackTrace) {
                return Container(
                  color: Colors.grey[300],
                  child: const Icon(Icons.image),
                );
              },
            ),
          ),
          Positioned(
            bottom: 4,
            left: 4,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: Colors.black.withOpacity(0.6),
                borderRadius: BorderRadius.circular(4),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (media.type == MediaType.video)
                    const Icon(Icons.play_arrow, color: Colors.white, size: 12),
                  const SizedBox(width: 2),
                  Text(
                    '${media.likesCount}',
                    style: const TextStyle(color: Colors.white, fontSize: 10),
                  ),
                  const SizedBox(width: 4),
                  const Icon(Icons.favorite, color: Colors.red, size: 12),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
