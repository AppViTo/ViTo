import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:intl/intl.dart';

import '../bloc/archive_bloc.dart';
import '../bloc/archive_event.dart';
import '../bloc/archive_state.dart';
import '../../domain/entities/archive.dart';
import '../../../../core/di/injection_container.dart';

class ArchiveCommentsWidget extends StatefulWidget {
  final String archiveId;
  final String? mediaId;
  
  const ArchiveCommentsWidget({
    super.key,
    required this.archiveId,
    this.mediaId,
  });
  
  @override
  State<ArchiveCommentsWidget> createState() => _ArchiveCommentsWidgetState();
}

class _ArchiveCommentsWidgetState extends State<ArchiveCommentsWidget> {
  late ArchiveBloc _archiveBloc;
  final TextEditingController _controller = TextEditingController();
  
  @override
  void initState() {
    super.initState();
    _archiveBloc = sl<ArchiveBloc>();
    _archiveBloc.add(LoadComments(
      archiveId: widget.archiveId,
      mediaId: widget.mediaId,
    ));
  }
  
  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
  
  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Expanded(
          child: BlocBuilder<ArchiveBloc, ArchiveState>(
            bloc: _archiveBloc,
            builder: (context, state) {
              if (state is ArchiveLoading) {
                return const Center(
                  child: CircularProgressIndicator(),
                );
              }
              
              if (state is CommentsLoaded) {
                if (state.comments.isEmpty) {
                  return const Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.comment, size: 64, color: Colors.grey),
                        SizedBox(height: 16),
                        Text(
                          'No comments yet',
                          style: TextStyle(color: Colors.grey),
                        ),
                      ],
                    ),
                  );
                }
                
                return ListView.builder(
                  padding: const EdgeInsets.all(8),
                  itemCount: state.comments.length,
                  itemBuilder: (context, index) {
                    final comment = state.comments[index];
                    return _buildCommentItem(comment);
                  },
                );
              }
              
              return const SizedBox.shrink();
            },
          ),
        ),
        
        Container(
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: Colors.grey[100],
            border: Border(
              top: BorderSide(color: Colors.grey[300]!),
            ),
          ),
          child: Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _controller,
                  decoration: InputDecoration(
                    hintText: 'Add a comment...',
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(24),
                      borderSide: BorderSide.none,
                    ),
                    filled: true,
                    fillColor: Colors.white,
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 8,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              IconButton(
                onPressed: _addComment,
                icon: const Icon(Icons.send, color: Colors.deepPurple),
              ),
            ],
          ),
        ),
      ],
    );
  }
  
  Widget _buildCommentItem(ArchiveComment comment) {
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 4),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                CircleAvatar(
                  radius: 16,
                  backgroundColor: Colors.deepPurple,
                  child: Text(
                    comment.displayName.isNotEmpty
                        ? comment.displayName[0].toUpperCase()
                        : comment.username[0].toUpperCase(),
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        comment.displayName.isNotEmpty
                            ? comment.displayName
                            : comment.username,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      if (comment.createdAt != null)
                        Text(
                          DateFormat('MMM d, yyyy HH:mm').format(comment.createdAt!),
                          style: TextStyle(
                            color: Colors.grey[500],
                            fontSize: 12,
                          ),
                        ),
                    ],
                  ),
                ),
                PopupMenuButton<String>(
                  itemBuilder: (context) => [
                    const PopupMenuItem(
                      value: 'edit',
                      child: Text('Edit'),
                    ),
                    const PopupMenuItem(
                      value: 'delete',
                      child: Text('Delete'),
                    ),
                  ],
                  onSelected: (value) {
                    if (value == 'delete') {
                      _archiveBloc.add(DeleteComment(commentId: comment.id));
                    }
                  },
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(comment.content),
            const SizedBox(height: 8),
            Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.favorite_border, size: 20),
                  onPressed: () {},
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                ),
                Text('${comment.likesCount}'),
              ],
            ),
          ],
        ),
      ),
    );
  }
  
  void _addComment() {
    final content = _controller.text.trim();
    if (content.isNotEmpty) {
      _archiveBloc.add(AddComment(
        archiveId: widget.archiveId,
        content: content,
        mediaId: widget.mediaId,
      ));
      _controller.clear();
    }
  }
}
