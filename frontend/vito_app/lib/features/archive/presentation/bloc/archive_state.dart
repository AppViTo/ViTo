import 'package:equatable/equatable.dart';

import '../../domain/entities/archive.dart';

abstract class ArchiveState extends Equatable {
  const ArchiveState();
  
  @override
  List<Object?> get props => [];
}

class ArchiveInitial extends ArchiveState {
  const ArchiveInitial();
}

class ArchiveLoading extends ArchiveState {
  const ArchiveLoading();
}

class ArchiveCreated extends ArchiveState {
  final EventArchive archive;
  
  const ArchiveCreated({required this.archive});
  
  @override
  List<Object?> get props => [archive];
}

class ArchiveLoaded extends ArchiveState {
  final EventArchive archive;
  
  const ArchiveLoaded({required this.archive});
  
  @override
  List<Object?> get props => [archive];
}

class PublicArchivesLoaded extends ArchiveState {
  final List<EventArchive> archives;
  
  const PublicArchivesLoaded({required this.archives});
  
  @override
  List<Object?> get props => [archives];
}

class MyArchivesLoaded extends ArchiveState {
  final List<EventArchive> archives;
  
  const MyArchivesLoaded({required this.archives});
  
  @override
  List<Object?> get props => [archives];
}

class ArchiveUpdated extends ArchiveState {
  final EventArchive archive;
  
  const ArchiveUpdated({required this.archive});
  
  @override
  List<Object?> get props => [archive];
}

class MediaUploading extends ArchiveState {
  const MediaUploading();
}

class MediaUploaded extends ArchiveState {
  final ArchiveMedia media;
  
  const MediaUploaded({required this.media});
  
  @override
  List<Object?> get props => [media];
}

class MediaLoaded extends ArchiveState {
  final List<ArchiveMedia> media;
  
  const MediaLoaded({required this.media});
  
  @override
  List<Object?> get props => [media];
}

class CommentAdded extends ArchiveState {
  final ArchiveComment comment;
  
  const CommentAdded({required this.comment});
  
  @override
  List<Object?> get props => [comment];
}

class CommentsLoaded extends ArchiveState {
  final List<ArchiveComment> comments;
  
  const CommentsLoaded({required this.comments});
  
  @override
  List<Object?> get props => [comments];
}

class CommentDeleted extends ArchiveState {
  final String commentId;
  
  const CommentDeleted({required this.commentId});
  
  @override
  List<Object?> get props => [commentId];
}

class MediaLiked extends ArchiveState {
  final String mediaId;
  
  const MediaLiked({required this.mediaId});
  
  @override
  List<Object?> get props => [mediaId];
}

class MediaUnliked extends ArchiveState {
  final String mediaId;
  
  const MediaUnliked({required this.mediaId});
  
  @override
  List<Object?> get props => [mediaId];
}

class ArchiveError extends ArchiveState {
  final String message;
  
  const ArchiveError({required this.message});
  
  @override
  List<Object?> get props => [message];
}
