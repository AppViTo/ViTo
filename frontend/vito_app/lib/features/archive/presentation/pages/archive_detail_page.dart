import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:share_plus/share_plus.dart';

import '../bloc/archive_bloc.dart';
import '../bloc/archive_event.dart';
import '../bloc/archive_state.dart';
import '../widgets/archive_stats_widget.dart';
import '../widgets/archive_media_grid_widget.dart';
import '../widgets/archive_comments_widget.dart';
import '../../../../core/di/injection_container.dart';

class ArchiveDetailPage extends StatefulWidget {
  final String archiveId;
  
  const ArchiveDetailPage({super.key, required this.archiveId});
  
  @override
  State<ArchiveDetailPage> createState() => _ArchiveDetailPageState();
}

class _ArchiveDetailPageState extends State<ArchiveDetailPage>
    with SingleTickerProviderStateMixin {
  late ArchiveBloc _archiveBloc;
  late TabController _tabController;
  
  @override
  void initState() {
    super.initState();
    _archiveBloc = sl<ArchiveBloc>();
    _tabController = TabController(length: 3, vsync: this);
    
    _archiveBloc.add(LoadArchive(archiveId: widget.archiveId));
  }
  
  @override
  void dispose() {
    _archiveBloc.close();
    _tabController.dispose();
    super.dispose();
  }
  
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Event Archive'),
        backgroundColor: Colors.deepPurple,
        actions: [
          IconButton(
            icon: const Icon(Icons.share),
            onPressed: _shareArchive,
          ),
          IconButton(
            icon: const Icon(Icons.edit),
            onPressed: _showEditDialog,
          ),
        ],
      ),
      body: BlocBuilder<ArchiveBloc, ArchiveState>(
        bloc: _archiveBloc,
        builder: (context, state) {
          if (state is ArchiveLoading) {
            return const Center(
              child: CircularProgressIndicator(),
            );
          }
          
          if (state is ArchiveLoaded) {
            return _buildArchiveDetail(state.archive);
          }
          
          if (state is ArchiveError) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.error, color: Colors.red, size: 64),
                  const SizedBox(height: 16),
                  Text(
                    state.message,
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 16),
                  ElevatedButton(
                    onPressed: () {
                      _archiveBloc.add(LoadArchive(archiveId: widget.archiveId));
                    },
                    child: const Text('Retry'),
                  ),
                ],
              ),
            );
          }
          
          return const SizedBox.shrink();
        },
      ),
    );
  }
  
  Widget _buildArchiveDetail(archive) {
    return Column(
      children: [
        if (archive.coverUrl != null)
          SizedBox(
            height: 200,
            width: double.infinity,
            child: Image.network(
              archive.coverUrl!,
              fit: BoxFit.cover,
              errorBuilder: (context, error, stackTrace) {
                return Container(
                  color: Colors.grey[300],
                  child: const Icon(Icons.image, size: 64),
                );
              },
            ),
          ),
        
        Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Text(
                      archive.title,
                      style: const TextStyle(
                        fontSize: 24,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  if (archive.isPublic)
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.green,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Text(
                        'Public',
                        style: TextStyle(color: Colors.white, fontSize: 12),
                      ),
                    ),
                ],
              ),
              if (archive.description != null) ...[
                const SizedBox(height: 8),
                Text(
                  archive.description!,
                  style: TextStyle(color: Colors.grey[600]),
                ),
              ],
              const SizedBox(height: 16),
              ArchiveStatsWidget(statistics: archive.statistics),
            ],
          ),
        ),
        
        TabBar(
          controller: _tabController,
          labelColor: Colors.deepPurple,
          unselectedLabelColor: Colors.grey,
          tabs: const [
            Tab(icon: Icon(Icons.photo_library), text: 'Media'),
            Tab(icon: Icon(Icons.comment), text: 'Comments'),
            Tab(icon: Icon(Icons.info), text: 'Details'),
          ],
        ),
        
        Expanded(
          child: TabBarView(
            controller: _tabController,
            children: [
              ArchiveMediaGridWidget(
                archiveId: widget.archiveId,
                onMediaTap: (media) {
                  // Show media detail
                },
              ),
              ArchiveCommentsWidget(
                archiveId: widget.archiveId,
              ),
              _buildDetailsTab(archive),
            ],
          ),
        ),
      ],
    );
  }
  
  Widget _buildDetailsTab(archive) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildDetailRow('Event Date', archive.eventDate?.toString() ?? 'N/A'),
          _buildDetailRow('Archived At', archive.archivedAt?.toString() ?? 'N/A'),
          _buildDetailRow('Share Code', archive.sharing.shareCode ?? 'N/A'),
          _buildDetailRow('Share URL', archive.sharing.shareUrl ?? 'N/A'),
          const SizedBox(height: 16),
          const Text(
            'Sharing Settings',
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 8),
          _buildSwitchRow('Public', archive.sharing.isPublic),
          _buildSwitchRow('Allow Comments', archive.sharing.allowComments),
          _buildSwitchRow('Allow Reactions', archive.sharing.allowReactions),
          _buildSwitchRow('Allow Sharing', archive.sharing.allowSharing),
        ],
      ),
    );
  }
  
  Widget _buildDetailRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(
              label,
              style: const TextStyle(
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          Expanded(
            child: Text(value),
          ),
        ],
      ),
    );
  }
  
  Widget _buildSwitchRow(String label, bool value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label),
          Switch(
            value: value,
            onChanged: null,
            activeColor: Colors.deepPurple,
          ),
        ],
      ),
    );
  }
  
  void _shareArchive() {
    final shareUrl = 'http://localhost:8080/archive/share/${_archiveBloc.state is ArchiveLoaded ? (_archiveBloc.state as ArchiveLoaded).archive.sharing.shareCode : ''}';
    Share.share('Check out this event archive: $shareUrl');
  }
  
  void _showEditDialog() {
    final state = _archiveBloc.state;
    if (state is ArchiveLoaded) {
      final archive = state.archive;
      showDialog(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Edit Archive'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                decoration: const InputDecoration(labelText: 'Title'),
                controller: TextEditingController(text: archive.title),
              ),
              const SizedBox(height: 8),
              TextField(
                decoration: const InputDecoration(labelText: 'Description'),
                controller: TextEditingController(text: archive.description),
                maxLines: 3,
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: () {
                _archiveBloc.add(UpdateArchive(
                  archiveId: archive.id,
                ));
                Navigator.pop(context);
              },
              child: const Text('Save'),
            ),
          ],
        ),
      );
    }
  }
}
