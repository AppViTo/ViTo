import 'dart:async';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../domain/entities/streaming.dart';
import '../../domain/repositories/streaming_repository.dart';
import 'streaming_event.dart';
import 'streaming_state.dart';

class StreamingBloc extends Bloc<StreamingEvent, StreamingState> {
  final StreamingRepository repository;
  
  StreamSubscription<Reaction>? _reactionSubscription;
  StreamSubscription<ChatMessage>? _chatSubscription;
  StreamSubscription<int>? _viewerCountSubscription;
  
  StreamingBloc({required this.repository}) : super(const StreamingInitial()) {
    on<CreateStream>(_onCreateStream);
    on<StartStream>(_onStartStream);
    on<StopStream>(_onStopStream);
    on<LoadStream>(_onLoadStream);
    on<LoadLiveStreams>(_onLoadLiveStreams);
    on<SendReaction>(_onSendReaction);
    on<SendChatMessage>(_onSendChatMessage);
    on<ReactionReceived>(_onReactionReceived);
    on<ChatMessageReceived>(_onChatMessageReceived);
    on<ViewerCountUpdated>(_onViewerCountUpdated);
    on<ConnectToStream>(_onConnectToStream);
    on<DisconnectFromStream>(_onDisconnectFromStream);
    
    _listenToStreams();
  }
  
  void _listenToStreams() {
    _reactionSubscription = repository.reactionStream.listen((reaction) {
      add(ReactionReceived(reaction: reaction));
    });
    
    _chatSubscription = repository.chatStream.listen((message) {
      add(ChatMessageReceived(message: message));
    });
    
    _viewerCountSubscription = repository.viewerCountStream.listen((count) {
      add(ViewerCountUpdated(count: count));
    });
  }
  
  Future<void> _onCreateStream(
    CreateStream event,
    Emitter<StreamingState> emit,
  ) async {
    emit(const StreamingLoading());
    
    final result = await repository.createStream(
      eventId: event.eventId,
      title: event.title,
      description: event.description,
      settings: event.settings,
    );
    
    result.fold(
      (failure) => emit(StreamingError(message: failure.message)),
      (stream) => emit(StreamCreated(stream: stream)),
    );
  }
  
  Future<void> _onStartStream(
    StartStream event,
    Emitter<StreamingState> emit,
  ) async {
    emit(const StreamingLoading());
    
    final result = await repository.startStream(event.streamId);
    
    result.fold(
      (failure) => emit(StreamingError(message: failure.message)),
      (stream) => emit(StreamActive(stream: stream)),
    );
  }
  
  Future<void> _onStopStream(
    StopStream event,
    Emitter<StreamingState> emit,
  ) async {
    emit(const StreamingLoading());
    
    final result = await repository.stopStream(event.streamId);
    
    result.fold(
      (failure) => emit(StreamingError(message: failure.message)),
      (stream) => emit(StreamEnded(stream: stream)),
    );
  }
  
  Future<void> _onLoadStream(
    LoadStream event,
    Emitter<StreamingState> emit,
  ) async {
    emit(const StreamingLoading());
    
    final result = await repository.getStream(event.streamId);
    
    result.fold(
      (failure) => emit(StreamingError(message: failure.message)),
      (stream) => emit(StreamLoaded(stream: stream)),
    );
  }
  
  Future<void> _onLoadLiveStreams(
    LoadLiveStreams event,
    Emitter<StreamingState> emit,
  ) async {
    emit(const StreamingLoading());
    
    final result = await repository.getLiveStreams(
      page: event.page,
      size: event.size,
    );
    
    result.fold(
      (failure) => emit(StreamingError(message: failure.message)),
      (streams) => emit(LiveStreamsLoaded(streams: streams)),
    );
  }
  
  Future<void> _onSendReaction(
    SendReaction event,
    Emitter<StreamingState> emit,
  ) async {
    final result = await repository.addReaction(
      streamId: event.streamId,
      type: event.type,
      emoji: event.emoji,
    );
    
    result.fold(
      (failure) => emit(ReactionError(message: failure.message)),
      (reaction) => emit(ReactionSent(reaction: reaction)),
    );
  }
  
  Future<void> _onSendChatMessage(
    SendChatMessage event,
    Emitter<StreamingState> emit,
  ) async {
    final result = await repository.sendChatMessage(
      streamId: event.streamId,
      message: event.message,
    );
    
    result.fold(
      (failure) => emit(ChatError(message: failure.message)),
      (message) => emit(ChatMessageSent(message: message)),
    );
  }
  
  void _onReactionReceived(
    ReactionReceived event,
    Emitter<StreamingState> emit,
  ) {
    if (state is StreamActive) {
      final currentState = state as StreamActive;
      emit(currentState.copyWith(
        reactions: [...currentState.reactions, event.reaction],
      ));
    }
  }
  
  void _onChatMessageReceived(
    ChatMessageReceived event,
    Emitter<StreamingState> emit,
  ) {
    if (state is StreamActive) {
      final currentState = state as StreamActive;
      emit(currentState.copyWith(
        messages: [...currentState.messages, event.message],
      ));
    }
  }
  
  void _onViewerCountUpdated(
    ViewerCountUpdated event,
    Emitter<StreamingState> emit,
  ) {
    if (state is StreamActive) {
      final currentState = state as StreamActive;
      emit(currentState.copyWith(viewerCount: event.count));
    }
  }
  
  Future<void> _onConnectToStream(
    ConnectToStream event,
    Emitter<StreamingState> emit,
  ) async {
    repository.connectToWebSocket(event.streamId, event.baseUrl);
  }
  
  Future<void> _onDisconnectFromStream(
    DisconnectFromStream event,
    Emitter<StreamingState> emit,
  ) async {
    repository.disconnectFromWebSocket();
  }
  
  @override
  Future<void> close() {
    _reactionSubscription?.cancel();
    _chatSubscription?.cancel();
    _viewerCountSubscription?.cancel();
    repository.disconnectFromWebSocket();
    return super.close();
  }
}
