import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:image_picker/image_picker.dart';
import '../../../../core/theme/app_colors.dart';
import '../../domain/entities/mosaic.dart';
import '../bloc/mosaic_bloc.dart';

class MosaicPage extends StatefulWidget {
  final String eventId;

  const MosaicPage({super.key, required this.eventId});

  @override
  State<MosaicPage> createState() => _MosaicPageState();
}

class _MosaicPageState extends State<MosaicPage> {
  bool _showGrid = true;
  final ImagePicker _imagePicker = ImagePicker();

  @override
  void initState() {
    super.initState();
    if (widget.eventId.isNotEmpty) {
      context.read<MosaicBloc>().add(LoadEventGrid(widget.eventId));
      context.read<MosaicBloc>().add(LoadEventFeed(widget.eventId));
    }
  }

  @override
  Widget build(BuildContext context) {
    if (widget.eventId.isEmpty) {
      return Scaffold(
        appBar: AppBar(title: const Text('Live Mosaic')),
        body: const Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.grid_view, size: 64, color: AppColors.grey400),
              SizedBox(height: 16),
              Text(
                'Select an event to view mosaic',
                style: TextStyle(fontSize: 16),
              ),
            ],
          ),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Live Mosaic'),
        actions: [
          // Toggle grid/feed view
          IconButton(
            icon: Icon(_showGrid ? Icons.list : Icons.grid_view),
            onPressed: () {
              setState(() {
                _showGrid = !_showGrid;
              });
            },
          ),
          // Upload button
          IconButton(
            icon: const Icon(Icons.add_a_photo),
            onPressed: _pickAndUpload,
          ),
        ],
      ),
      body: BlocBuilder<MosaicBloc, MosaicState>(
        builder: (context, state) {
          if (state is MosaicLoading) {
            return const Center(
              child: CircularProgressIndicator(color: AppColors.primary),
            );
          }

          if (state is MosaicError) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.error_outline, size: 64, color: AppColors.error),
                  const SizedBox(height: 16),
                  Text(state.message),
                  const SizedBox(height: 16),
                  ElevatedButton(
                    onPressed: () {
                      context.read<MosaicBloc>().add(LoadEventGrid(widget.eventId));
                    },
                    child: const Text('Retry'),
                  ),
                ],
              ),
            );
          }

          if (_showGrid) {
            return _buildGrid(state);
          } else {
            return _buildFeed(state);
          }
        },
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _pickAndUpload,
        child: const Icon(Icons.camera_alt),
      ),
    );
  }

  Widget _buildGrid(MosaicState state) {
    if (state is MosaicGridLoaded) {
      final items = state.grid.grid.expand((row) => row).where((item) => item != null).toList();
      if (items.isEmpty) {
        return _buildEmptyState();
      }

      return GridView.builder(
        padding: const EdgeInsets.all(4),
        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: 3,
          crossAxisSpacing: 4,
          mainAxisSpacing: 4,
        ),
        itemCount: items.length,
        itemBuilder: (context, index) {
          final item = items[index]!;
          return GestureDetector(
            onTap: () => _showItemDetail(item),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(4),
                child: Image.network(
                  item.mediaUrl ?? '',
                fit: BoxFit.cover,
                errorBuilder: (context, error, stackTrace) => Container(
                  color: AppColors.grey200,
                  child: const Icon(Icons.image, color: AppColors.grey400),
                ),
              ),
            ),
          );
        },
      );
    }

    return _buildEmptyState();
  }

  Widget _buildFeed(MosaicState state) {
    if (state is MosaicFeedLoaded) {
      final items = state.feed;
      if (items.isEmpty) {
        return _buildEmptyState();
      }

      return ListView.builder(
        padding: const EdgeInsets.all(8),
        itemCount: items.length,
        itemBuilder: (context, index) {
          final item = items[index];
          return Card(
            margin: const EdgeInsets.only(bottom: 8),
            clipBehavior: Clip.antiAlias,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Image
                if (item.mediaUrls.isNotEmpty)
                  Image.network(
                    item.mediaUrls.first,
                    width: double.infinity,
                    height: 300,
                    fit: BoxFit.cover,
                    errorBuilder: (context, error, stackTrace) => Container(
                      height: 300,
                      color: AppColors.grey200,
                      child: const Icon(Icons.image, size: 48, color: AppColors.grey400),
                    ),
                  ),
                // Actions
                Padding(
                  padding: const EdgeInsets.all(8),
                  child: Row(
                    children: [
                      IconButton(
                        icon: Icon(
                          item.isLiked ? Icons.favorite : Icons.favorite_border,
                          color: item.isLiked ? AppColors.error : null,
                        ),
                        onPressed: () {
                          if (item.isLiked) {
                            context.read<MosaicBloc>().add(UnlikePost(item.id));
                          } else {
                            context.read<MosaicBloc>().add(LikePost(item.id));
                          }
                        },
                      ),
                      Text('${item.likesCount}'),
                      const Spacer(),
                      Text(
                        item.displayName,
                        style: const TextStyle(color: AppColors.textSecondary),
                      ),
                    ],
                  ),
                ),
                // Caption
                if (item.content != null)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(12, 0, 12, 8),
                    child: Text(item.content!),
                  ),
              ],
            ),
          );
        },
      );
    }

    return _buildEmptyState();
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.grid_view, size: 64, color: AppColors.grey400),
          const SizedBox(height: 16),
          const Text(
            'No content yet',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
          ),
          const SizedBox(height: 8),
          const Text(
            'Be the first to share a moment!',
            style: TextStyle(color: AppColors.textSecondary),
          ),
          const SizedBox(height: 24),
          ElevatedButton.icon(
            onPressed: _pickAndUpload,
            icon: const Icon(Icons.camera_alt),
            label: const Text('Upload Photo'),
          ),
        ],
      ),
    );
  }

  Future<void> _pickAndUpload() async {
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      builder: (context) => SafeArea(
        child: Wrap(
          children: [
            ListTile(
              leading: const Icon(Icons.camera_alt),
              title: const Text('Camera'),
              onTap: () => Navigator.pop(context, ImageSource.camera),
            ),
            ListTile(
              leading: const Icon(Icons.photo_library),
              title: const Text('Gallery'),
              onTap: () => Navigator.pop(context, ImageSource.gallery),
            ),
          ],
        ),
      ),
    );

    if (source == null || !mounted) return;

    final XFile? image = await _imagePicker.pickImage(source: source);
    if (image == null || !mounted) return;

    // TODO: Upload to media service and then add to mosaic
    // For now, just show a snackbar
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Upload feature coming soon!'),
      ),
    );
  }

  void _showItemDetail(MosaicItem item) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (context) => DraggableScrollableSheet(
        initialChildSize: 0.9,
        minChildSize: 0.5,
        maxChildSize: 0.95,
        expand: false,
        builder: (context, scrollController) {
          return SingleChildScrollView(
            controller: scrollController,
            child: Column(
              children: [
                // Handle
                Container(
                  margin: const EdgeInsets.only(top: 8),
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: AppColors.grey300,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
                // Image
                if (item.mediaUrl != null)
                  Image.network(
                    item.mediaUrl!,
                    width: double.infinity,
                    fit: BoxFit.contain,
                  ),
                // Actions
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: Row(
                    children: [
                      Text(item.displayName, style: const TextStyle(fontWeight: FontWeight.w500)),
                      const Spacer(),
                      Text('${item.likesCount} likes'),
                    ],
                  ),
                ),
                if (item.content != null)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Text(item.content!),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }
}
