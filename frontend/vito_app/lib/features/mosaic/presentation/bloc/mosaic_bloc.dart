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

/// `LIKE_UPDATE` por WebSocket: nuevo total de likes de [update.itemId].
class LikeCountUpdated extends MosaicEvent {
  final MosaicLikeCountUpdate update;

  const LikeCountUpdated(this.update);

  @override
  List<Object?> get props => [update];
}

/// El organizador retira una foto (`removeMosaicItem`, T-F10).
class RemoveMosaicItem extends MosaicEvent {
  final String itemId;

  const RemoveMosaicItem(this.itemId);

  @override
  List<Object?> get props => [itemId];
}

/// Llegó `ITEM_REMOVED` por el WebSocket del mosaico (T-F10).
class MosaicItemRemovedReceived extends MosaicEvent {
  final String itemId;

  const MosaicItemRemovedReceived(this.itemId);

  @override
  List<Object?> get props => [itemId];
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

class MosaicError extends MosaicState {
  final String message;
  
  const MosaicError(this.message);
  
  @override
  List<Object?> get props => [message];
}

/// Falló `removeMosaicItem` (`403`, `404`...). Es transitorio: el bloc vuelve
/// enseguida al estado anterior, así que la página solo lo usa para avisar.
class MosaicItemRemovalFailed extends MosaicState {
  final String itemId;
  final String message;

  const MosaicItemRemovalFailed({required this.itemId, required this.message});

  @override
  List<Object?> get props => [itemId, message];
}

// BLoC
class MosaicBloc extends Bloc<MosaicEvent, MosaicState> {
  final MosaicRepository repository;
  StreamSubscription? _feedSubscription;
  StreamSubscription? _gridSubscription;
  StreamSubscription? _likeSubscription;
  StreamSubscription? _itemRemovedSubscription;
  
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
    on<RemoveMosaicItem>(_onRemoveMosaicItem);
    on<MosaicItemRemovedReceived>(_onMosaicItemRemovedReceived);
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
      (update) {
        add(LikeCountUpdated(update));
      },
    );

    _itemRemovedSubscription?.cancel();
    _itemRemovedSubscription = repository.itemRemovedStream.listen(
      (itemId) {
        add(MosaicItemRemovedReceived(itemId));
      },
    );
  }
  
  void _onDisconnectFromWebSocket(DisconnectFromWebSocket event, Emitter<MosaicState> emit) {
    repository.disconnectFromWebSocket();
    
    _feedSubscription?.cancel();
    _gridSubscription?.cancel();
    _likeSubscription?.cancel();
    _itemRemovedSubscription?.cancel();
  }
  
  /// `NEW_ITEM`: el ítem entra arriba del feed cargado (si no estaba ya).
  /// Con otra vista, la grilla se actualiza por `GRID_UPDATE`.
  void _onNewItemReceived(NewItemReceived event, Emitter<MosaicState> emit) {
    final current = state;
    if (current is! MosaicFeedLoaded) return;
    if (current.feed.any((item) => item.id == event.item.id)) return;
    emit(MosaicFeedLoaded([event.item, ...current.feed]));
  }

  /// `GRID_UPDATE`: reemplaza la grilla cargada, conservando la vista.
  void _onGridUpdated(GridUpdated event, Emitter<MosaicState> emit) {
    final current = state;
    if (current is MosaicGridLoaded || current is MosaicGridUpdated) {
      emit(MosaicGridLoaded(event.grid));
    }
  }

  /// `LIKE_UPDATE`: cambia el total del ítem y conserva el `isLiked` propio.
  void _onLikeCountUpdated(LikeCountUpdated event, Emitter<MosaicState> emit) {
    final current = state;
    if (current is! MosaicFeedLoaded) return;
    final update = event.update;
    if (!current.feed.any((item) => item.id == update.itemId)) return;
    emit(MosaicFeedLoaded([
      for (final item in current.feed)
        item.id == update.itemId
            ? item.withLikeState(MosaicLikeState(
                itemId: update.itemId,
                likesCount: update.likesCount,
                isLiked: item.isLiked,
              ))
            : item,
    ]));
  }

  Future<void> _onRemoveMosaicItem(
    RemoveMosaicItem event,
    Emitter<MosaicState> emit,
  ) async {
    final result = await repository.removeMosaicItem(event.itemId);

    result.fold(
      (failure) {
        final previous = state;
        emit(MosaicItemRemovalFailed(
          itemId: event.itemId,
          message: failure.message,
        ));
        emit(previous);
      },
      (_) => _removeItemFromState(event.itemId, emit),
    );
  }

  void _onMosaicItemRemovedReceived(
    MosaicItemRemovedReceived event,
    Emitter<MosaicState> emit,
  ) {
    _removeItemFromState(event.itemId, emit);
  }

  /// Post-moderación (A3): el ítem sale del feed y su celda vuelve a `null`.
  void _removeItemFromState(String itemId, Emitter<MosaicState> emit) {
    final current = state;
    if (current is MosaicFeedLoaded) {
      final feed = current.feed.where((item) => item.id != itemId).toList();
      if (feed.length != current.feed.length) {
        emit(MosaicFeedLoaded(feed));
      }
    } else if (current is MosaicGridLoaded) {
      final grid = _withoutItem(current.grid, itemId);
      if (grid != null) emit(MosaicGridLoaded(grid));
    } else if (current is MosaicGridUpdated) {
      final grid = _withoutItem(current.grid, itemId);
      if (grid != null) emit(MosaicGridUpdated(grid));
    }
  }

  /// Copia de [grid] con la celda de [itemId] en `null`, o `null` si el ítem
  /// no está en la grilla.
  MosaicGrid? _withoutItem(MosaicGrid grid, String itemId) {
    var found = false;
    final cells = grid.grid
        .map((row) => row.map((cell) {
              if (cell != null && cell.id == itemId) {
                found = true;
                return null;
              }
              return cell;
            }).toList())
        .toList();
    if (!found) return null;
    return MosaicGrid(
      eventId: grid.eventId,
      grid: cells,
      totalItems: grid.totalItems > 0 ? grid.totalItems - 1 : 0,
      lastUpdatedIndex: grid.lastUpdatedIndex,
      lastUpdatedAt: grid.lastUpdatedAt,
    );
  }
  
  @override
  Future<void> close() {
    _feedSubscription?.cancel();
    _gridSubscription?.cancel();
    _likeSubscription?.cancel();
    _itemRemovedSubscription?.cancel();
    repository.disconnectFromWebSocket();
    return super.close();
  }
}
