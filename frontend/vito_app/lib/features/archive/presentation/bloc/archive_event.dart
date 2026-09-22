import 'package:equatable/equatable.dart';

import '../../domain/entities/archive.dart';

abstract class ArchiveEvent extends Equatable {
  const ArchiveEvent();
  
  @override
  List<Object?> get props => [];
}

class CreateArchive extends ArchiveEvent {
  final String eventId;
  final String title;
  final String? description;
  
  const CreateArchive({
    required this.eventId,
    required this.title,
    this.description,
  });
  
  @override
  List<Object?> get props => [eventId, title, description];
}

class LoadArchive extends ArchiveEvent {
  final String archiveId;
  
  const LoadArchive({required this.archiveId});
  
  @override
  List<Object?> get props => [archiveId];
}

class LoadPublicArchives extends ArchiveEvent {
  final int page;
  final int size;
  
  const LoadPublicArchives({this.page = 0, this.size = 20});
  
  @override
  List<Object?> get props => [page, size];
}

class LoadMyArchives extends ArchiveEvent {
  final int page;
  final int size;
  
  const LoadMyArchives({this.page = 0, this.size = 20});
  
  @override
  List<Object?> get props => [page, size];
}

class UpdateArchive extends ArchiveEvent {
  final String archiveId;
  final String? title;
  final String? description;
  final String? coverUrl;
  final bool? isPublic;
  final bool? allowComments;
  final bool? allowReactions;
  final bool? allowSharing;
  
  const UpdateArchive({
    required this.archiveId,
    this.title,
    this.description,
    this.coverUrl,
    this.isPublic,
    this.allowComments,
    this.allowReactions,
    this.allowSharing,
  });
  
  @override
  List<Object?> get props => [
        archiveId,
        title,
        description,
        coverUrl,
        isPublic,
        allowComments,
        allowReactions,
        allowSharing,
      ];
}

class UploadMedia extends ArchiveEvent {
  final String archiveId;
  final MediaType type;
  final String url;
  final String? thumbnailUrl;
  final String? caption;
  final MediaMetadata? metadata;
  
  const UploadMedia({
    required this.archiveId,
    required this.type,
    required this.url,
    this.thumbnailUrl,
    this.caption,
    this.metadata,
  });
  
  @override
  List<Object?> get props => [archiveId, type, url, thumbnailUrl, caption, metadata];
}

class LoadMedia extends ArchiveEvent {
  final String archiveId;
  final int page;
  final int size;
  
  const LoadMedia({
    required this.archiveId,
    this.page = 0,
    this.size = 50,
  });
  
  @override
  List<Object?> get props => [archiveId, page, size];
}

class AddComment extends ArchiveEvent {
  final String archiveId;
  final String content;
  final String? mediaId;
  final String? parentId;
  
  const AddComment({
    required this.archiveId,
    required this.content,
    this.mediaId,
    this.parentId,
  });
  
  @override
  List<Object?> get props => [archiveId, content, mediaId, parentId];
}

class LoadComments extends ArchiveEvent {
  final String archiveId;
  final String? mediaId;
  final int page;
  final int size;
  
  const LoadComments({
    required this.archiveId,
    this.mediaId,
    this.page = 0,
    this.size = 50,
  });
  
  @override
  List<Object?> get props => [archiveId, mediaId, page, size];
}

class DeleteComment extends ArchiveEvent {
  final String commentId;
  
  const DeleteComment({required this.commentId});
  
  @override
  List<Object?> get props => [commentId];
}

class LikeMedia extends ArchiveEvent {
  final String mediaId;
  
  const LikeMedia({required this.mediaId});
  
  @override
  List<Object?> get props => [mediaId];
}

class UnlikeMedia extends ArchiveEvent {
  final String mediaId;
  
  const UnlikeMedia({required this.mediaId});
  
  @override
  List<Object?> get props => [mediaId];
}
