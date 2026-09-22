import 'package:flutter_bloc/flutter_bloc.dart';

import '../../domain/repositories/archive_repository.dart';
import 'archive_event.dart';
import 'archive_state.dart';

class ArchiveBloc extends Bloc<ArchiveEvent, ArchiveState> {
  final ArchiveRepository repository;
  
  ArchiveBloc({required this.repository}) : super(ArchiveInitial()) {
    on<CreateArchive>(_onCreateArchive);
    on<LoadArchive>(_onLoadArchive);
    on<LoadPublicArchives>(_onLoadPublicArchives);
    on<LoadMyArchives>(_onLoadMyArchives);
    on<UpdateArchive>(_onUpdateArchive);
    on<UploadMedia>(_onUploadMedia);
    on<LoadMedia>(_onLoadMedia);
    on<AddComment>(_onAddComment);
    on<LoadComments>(_onLoadComments);
    on<DeleteComment>(_onDeleteComment);
    on<LikeMedia>(_onLikeMedia);
    on<UnlikeMedia>(_onUnlikeMedia);
  }
  
  Future<void> _onCreateArchive(
    CreateArchive event,
    Emitter<ArchiveState> emit,
  ) async {
    emit(ArchiveLoading());
    
    final result = await repository.createArchive(
      eventId: event.eventId,
      title: event.title,
      description: event.description,
    );
    
    result.fold(
      (failure) => emit(ArchiveError(message: failure.message)),
      (archive) => emit(ArchiveCreated(archive: archive)),
    );
  }
  
  Future<void> _onLoadArchive(
    LoadArchive event,
    Emitter<ArchiveState> emit,
  ) async {
    emit(ArchiveLoading());
    
    final result = await repository.getArchive(event.archiveId);
    
    result.fold(
      (failure) => emit(ArchiveError(message: failure.message)),
      (archive) => emit(ArchiveLoaded(archive: archive)),
    );
  }
  
  Future<void> _onLoadPublicArchives(
    LoadPublicArchives event,
    Emitter<ArchiveState> emit,
  ) async {
    emit(ArchiveLoading());
    
    final result = await repository.getPublicArchives(
      page: event.page,
      size: event.size,
    );
    
    result.fold(
      (failure) => emit(ArchiveError(message: failure.message)),
      (archives) => emit(PublicArchivesLoaded(archives: archives)),
    );
  }
  
  Future<void> _onLoadMyArchives(
    LoadMyArchives event,
    Emitter<ArchiveState> emit,
  ) async {
    emit(ArchiveLoading());
    
    final result = await repository.getUserArchives(
      page: event.page,
      size: event.size,
    );
    
    result.fold(
      (failure) => emit(ArchiveError(message: failure.message)),
      (archives) => emit(MyArchivesLoaded(archives: archives)),
    );
  }
  
  Future<void> _onUpdateArchive(
    UpdateArchive event,
    Emitter<ArchiveState> emit,
  ) async {
    emit(ArchiveLoading());
    
    final result = await repository.updateArchive(
      archiveId: event.archiveId,
      title: event.title,
      description: event.description,
      coverUrl: event.coverUrl,
      isPublic: event.isPublic,
      allowComments: event.allowComments,
      allowReactions: event.allowReactions,
      allowSharing: event.allowSharing,
    );
    
    result.fold(
      (failure) => emit(ArchiveError(message: failure.message)),
      (archive) => emit(ArchiveUpdated(archive: archive)),
    );
  }
  
  Future<void> _onUploadMedia(
    UploadMedia event,
    Emitter<ArchiveState> emit,
  ) async {
    emit(MediaUploading());
    
    final result = await repository.uploadMedia(
      archiveId: event.archiveId,
      type: event.type,
      url: event.url,
      thumbnailUrl: event.thumbnailUrl,
      caption: event.caption,
      metadata: event.metadata,
    );
    
    result.fold(
      (failure) => emit(ArchiveError(message: failure.message)),
      (media) => emit(MediaUploaded(media: media)),
    );
  }
  
  Future<void> _onLoadMedia(
    LoadMedia event,
    Emitter<ArchiveState> emit,
  ) async {
    emit(ArchiveLoading());
    
    final result = await repository.getMedia(
      archiveId: event.archiveId,
      page: event.page,
      size: event.size,
    );
    
    result.fold(
      (failure) => emit(ArchiveError(message: failure.message)),
      (media) => emit(MediaLoaded(media: media)),
    );
  }
  
  Future<void> _onAddComment(
    AddComment event,
    Emitter<ArchiveState> emit,
  ) async {
    final result = await repository.addComment(
      archiveId: event.archiveId,
      content: event.content,
      mediaId: event.mediaId,
      parentId: event.parentId,
    );
    
    result.fold(
      (failure) => emit(ArchiveError(message: failure.message)),
      (comment) => emit(CommentAdded(comment: comment)),
    );
  }
  
  Future<void> _onLoadComments(
    LoadComments event,
    Emitter<ArchiveState> emit,
  ) async {
    emit(ArchiveLoading());
    
    final result = await repository.getComments(
      archiveId: event.archiveId,
      mediaId: event.mediaId,
      page: event.page,
      size: event.size,
    );
    
    result.fold(
      (failure) => emit(ArchiveError(message: failure.message)),
      (comments) => emit(CommentsLoaded(comments: comments)),
    );
  }
  
  Future<void> _onDeleteComment(
    DeleteComment event,
    Emitter<ArchiveState> emit,
  ) async {
    final result = await repository.deleteComment(event.commentId);
    
    result.fold(
      (failure) => emit(ArchiveError(message: failure.message)),
      (_) => emit(CommentDeleted(commentId: event.commentId)),
    );
  }
  
  Future<void> _onLikeMedia(
    LikeMedia event,
    Emitter<ArchiveState> emit,
  ) async {
    final result = await repository.likeMedia(event.mediaId);
    
    result.fold(
      (failure) => emit(ArchiveError(message: failure.message)),
      (_) => emit(MediaLiked(mediaId: event.mediaId)),
    );
  }
  
  Future<void> _onUnlikeMedia(
    UnlikeMedia event,
    Emitter<ArchiveState> emit,
  ) async {
    final result = await repository.unlikeMedia(event.mediaId);
    
    result.fold(
      (failure) => emit(ArchiveError(message: failure.message)),
      (_) => emit(MediaUnliked(mediaId: event.mediaId)),
    );
  }
}
