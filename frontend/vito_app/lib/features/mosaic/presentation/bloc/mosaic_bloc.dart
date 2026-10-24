import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:equatable/equatable.dart';

import '../../domain/entities/mosaic.dart';
import '../../domain/repositories/mosaic_repository.dart';

// Events
abstract class MosaicEvent extends Equatable {
  const MosaicEvent();
  
  @override
  List<Object?> get props => [];
}

/// Sube una foto al mosaico en dos pasos (`uploadMedia` y
/// `createMosaicPost`, ADR-016).
class UploadMosaicPhoto extends MosaicEvent {
  final String eventId;
  final Uint8List bytes;
  final String fileName;
  final String mimeType;
  final String? content;

  const UploadMosaicPhoto({
    required this.eventId,
    required this.bytes,
    required this.fileName,
    required this.mimeType,
    this.content,
  });

  @override
  List<Object?> get props => [eventId, bytes, fileName, mimeType, content];
}

class LoadEventFeed extends MosaicEvent {
  final String eventId;
  
  const LoadEventFeed(this.eventId);
  
  @override
  List<Object?> get props => [eventId];
}

class LoadEventGrid extends MosaicEvent {
  final String eventId;
  
  const LoadEventGrid(this.eventId);
  
  @override
  List<Object?> get props => [eventId];
}

class InitializeGrid extends MosaicEvent {
  final String eventId;
  final int rows;
  final int cols;
  
  const InitializeGrid({
    required this.eventId,
    this.rows = 4,
    this.cols = 4,
  });
  
  @override
  List<Object?> get props => [eventId, rows, cols];
}

class LikePost extends MosaicEvent {
  final String itemId;
  
  const LikePost(this.itemId);
  
  @override
  List<Object?> get props => [itemId];
}

class UnlikePost extends MosaicEvent {
  final String itemId;
  
  const UnlikePost(this.itemId);
  
  @override
  List<Object?> get props => [itemId];
}

class ConnectToWebSocket extends MosaicEvent {
  final String eventId;
  
  const ConnectToWebSocket({
    required this.eventId,
  });
  
  @override
  List<Object?> get props => [eventId];
}

class DisconnectFromWebSocket extends MosaicEvent {}

class NewItemReceived extends MosaicEvent {
  final MosaicFeedItem item;
  
  const NewItemReceived(this.item);
  
  @override
  List<Object?> get props => [item];
}

class GridUpdated extends MosaicEvent {
  final MosaicGrid grid;
  
  const GridUpdated(this.grid);
  
  @override
  List<Object?> get props => [grid];
}

class LikeCountUpdated extends MosaicEvent {
  final int likesCount;
  
  const LikeCountUpdated(this.likesCount);
  
  @override
  List<Object?> get props => [likesCount];
}

// States
abstract class MosaicState extends Equatable {
  const MosaicState();
  
  @override
  List<Object?> get props => [];
}

class MosaicInitial extends MosaicState {}

class MosaicLoading extends MosaicState {}

class MosaicFeedLoaded extends MosaicState {
  final List<MosaicFeedItem> feed;
  
  const MosaicFeedLoaded(this.feed);
  
  @override
  List<Object?> get props => [feed];
}

class MosaicGridLoaded extends MosaicState {
  final MosaicGrid grid;
  
  const MosaicGridLoaded(this.grid);
  
  @override
  List<Object?> get props => [grid];
}

class MosaicItemAdded extends MosaicState {
  final MosaicFeedItem item;
  
  const MosaicItemAdded(this.item);
  
  @override
  List<Object?> get props => [item];
}

/// Subida en curso; la pantalla mantiene el contenido que ya mostraba.
class MosaicUploading extends MosaicState {}

/// La subida falló; se informa sin reemplazar el feed ni la grilla.
class MosaicUploadFailed extends MosaicState {
  final String message;

  const MosaicUploadFailed(this.message);

  @override
  List<Object?> get props => [message];
}

class MosaicRealTimeUpdate extends MosaicState {
  final MosaicFeedItem item;
  
  const MosaicRealTimeUpdate(this.item);
  
  @override
  List<Object?> get props => [item];
}

class MosaicGridUpdated extends MosaicState {
  final MosaicGrid grid;
  
  const MosaicGridUpdated(this.grid);
  
  @override
  List<Object?> get props => [grid];
}

class MosaicLikeUpdated extends MosaicState {
  final int likesCount;
  
  const MosaicLikeUpdated(this.likesCount);
  
  @override
  List<Object?> get props => [likesCount];
}

class MosaicError extends MosaicState {
  final String message;
  
  const MosaicError(this.message);
  
  @override
  List<Object?> get props => [message];
}

// BLoC
class MosaicBloc extends Bloc<MosaicEvent, MosaicState> {
  final MosaicRepository repository;
  StreamSubscription? _feedSubscription;
  StreamSubscription? _gridSubscription;
  StreamSubscription? _likeSubscription;
  
  MosaicBloc({required this.repository}) : super(MosaicInitial()) {
    on<UploadMosaicPhoto>(_onUploadMosaicPhoto);
    on<LoadEventFeed>(_onLoadEventFeed);
    on<LoadEventGrid>(_onLoadEventGrid);
    on<InitializeGrid>(_onInitializeGrid);
    on<LikePost>(_onLikePost);
    on<UnlikePost>(_onUnlikePost);
    on<ConnectToWebSocket>(_onConnectToWebSocket);
    on<DisconnectFromWebSocket>(_onDisconnectFromWebSocket);
    on<NewItemReceived>(_onNewItemReceived);
    on<GridUpdated>(_onGridUpdated);
    on<LikeCountUpdated>(_onLikeCountUpdated);
  }
  
  Future<void> _onUploadMosaicPhoto(
    UploadMosaicPhoto event,
    Emitter<MosaicState> emit,
  ) async {
    final previous = state;
    emit(MosaicUploading());

    final result = await repository.uploadPhoto(
      eventId: event.eventId,
      bytes: event.bytes,
      fileName: event.fileName,
      mimeType: event.mimeType,
      content: event.content,
    );

    result.fold(
      (failure) {
        emit(MosaicUploadFailed(failure.message));
        // Vuelve al feed o la grilla que había para no perder lo cargado.
        if (previous is MosaicFeedLoaded || previous is MosaicGridLoaded) {
          emit(previous);
        }
      },
      (item) => emit(MosaicItemAdded(item)),
    );
  }
  
  Future<void> _onLoadEventFeed(LoadEventFeed event, Emitter<MosaicState> emit) async {
    emit(MosaicLoading());
    
    final result = await repository.getEventFeed(eventId: event.eventId);
    
    result.fold(
      (failure) => emit(MosaicError(failure.message)),
      (feed) => emit(MosaicFeedLoaded(feed)),
    );
  }
  
  Future<void> _onLoadEventGrid(LoadEventGrid event, Emitter<MosaicState> emit) async {
    emit(MosaicLoading());
    
    final result = await repository.getEventGrid(event.eventId);
    
    result.fold(
      (failure) => emit(MosaicError(failure.message)),
      (grid) => emit(MosaicGridLoaded(grid)),
    );
  }
  
  Future<void> _onInitializeGrid(InitializeGrid event, Emitter<MosaicState> emit) async {
    emit(MosaicLoading());
    
    final result = await repository.initializeGrid(
      eventId: event.eventId,
      rows: event.rows,
      cols: event.cols,
    );
    
    result.fold(
      (failure) => emit(MosaicError(failure.message)),
      (grid) => emit(MosaicGridLoaded(grid)),
    );
  }
  
  Future<void> _onLikePost(LikePost event, Emitter<MosaicState> emit) async {
    final result = await repository.likePost(event.itemId);

    result.fold(
      (failure) => emit(MosaicError(failure.message)),
      (likeState) => _applyLikeState(likeState, emit),
    );
  }

  Future<void> _onUnlikePost(UnlikePost event, Emitter<MosaicState> emit) async {
    final result = await repository.unlikePost(event.itemId);

    result.fold(
      (failure) => emit(MosaicError(failure.message)),
      (likeState) => _applyLikeState(likeState, emit),
    );
  }

  /// Aplica el `MosaicLikeState` de la respuesta al ítem del feed cargado.
  void _applyLikeState(MosaicLikeState likeState, Emitter<MosaicState> emit) {
    final current = state;
    if (current is! MosaicFeedLoaded) return;
    emit(MosaicFeedLoaded([
      for (final item in current.feed)
        item.id == likeState.itemId ? item.withLikeState(likeState) : item,
    ]));
  }
  
  void _onConnectToWebSocket(ConnectToWebSocket event, Emitter<MosaicState> emit) {
    repository.connectToWebSocket(event.eventId);
    
    _feedSubscription?.cancel();
    _gridSubscription?.cancel();
    _likeSubscription?.cancel();
    
    _feedSubscription = repository.feedStream.listen(
      (item) {
        add(NewItemReceived(item));
      },
    );
    
    _gridSubscription = repository.gridStream.listen(
      (grid) {
        add(GridUpdated(grid));
      },
    );
    
    _likeSubscription = repository.likeStream.listen(
      (likesCount) {
        add(LikeCountUpdated(likesCount));
      },
    );
  }
  
  void _onDisconnectFromWebSocket(DisconnectFromWebSocket event, Emitter<MosaicState> emit) {
    repository.disconnectFromWebSocket();
    
    _feedSubscription?.cancel();
    _gridSubscription?.cancel();
    _likeSubscription?.cancel();
  }
  
  void _onNewItemReceived(NewItemReceived event, Emitter<MosaicState> emit) {
    emit(MosaicRealTimeUpdate(event.item));
  }
  
  void _onGridUpdated(GridUpdated event, Emitter<MosaicState> emit) {
    emit(MosaicGridUpdated(event.grid));
  }
  
  void _onLikeCountUpdated(LikeCountUpdated event, Emitter<MosaicState> emit) {
    emit(MosaicLikeUpdated(event.likesCount));
  }
  
  @override
  Future<void> close() {
    _feedSubscription?.cancel();
    _gridSubscription?.cancel();
    _likeSubscription?.cancel();
    repository.disconnectFromWebSocket();
    return super.close();
  }
}
