import 'package:flutter_bloc/flutter_bloc.dart';
import 'chat_event.dart';
import 'chat_state.dart';
import '../../domain/repositories/chat_repository.dart';
import '../../domain/entities/chat_message.dart';
import '../../domain/entities/chat_room.dart';

class ChatBloc extends Bloc<ChatEvent, ChatState> {
  final ChatRepository repository;
  
  ChatBloc({required this.repository}) : super(ChatInitial()) {
    on<LoadRooms>(_onLoadRooms);
    on<JoinRoom>(_onJoinRoom);
    on<LeaveRoom>(_onLeaveRoom);
    on<LoadMessages>(_onLoadMessages);
    on<SendMessage>(_onSendMessage);
    on<MessageReceived>(_onMessageReceived);
    on<RoomUpdated>(_onRoomUpdated);
    on<SendTypingIndicator>(_onSendTypingIndicator);
    on<MarkAsRead>(_onMarkAsRead);
  }
  
  Future<void> _onLoadRooms(LoadRooms event, Emitter<ChatState> emit) async {
    try {
      emit(ChatLoading());
      final rooms = await repository.getRoomsByUserId(event.userId);
      emit(RoomsLoaded(rooms: rooms));
    } catch (e) {
      emit(ChatError(message: e.toString()));
    }
  }
  
  Future<void> _onJoinRoom(JoinRoom event, Emitter<ChatState> emit) async {
    try {
      emit(ChatLoading());
      final room = await repository.joinRoom(event.roomId);
      
      // Load initial messages
      final messages = await repository.getMessages(roomId: event.roomId);
      
      emit(RoomJoined(
        room: room,
        messages: messages,
      ));
    } catch (e) {
      emit(ChatError(message: e.toString()));
    }
  }
  
  Future<void> _onLeaveRoom(LeaveRoom event, Emitter<ChatState> emit) async {
    try {
      await repository.leaveRoom(event.roomId);
      emit(RoomLeft());
    } catch (e) {
      emit(ChatError(message: e.toString()));
    }
  }
  
  Future<void> _onLoadMessages(LoadMessages event, Emitter<ChatState> emit) async {
    try {
      final messages = await repository.getMessages(
        roomId: event.roomId,
        page: event.page,
      );
      
      if (state is RoomJoined) {
        final currentState = state as RoomJoined;
        emit(currentState.copyWith(messages: messages));
      }
    } catch (e) {
      emit(ChatError(message: e.toString()));
    }
  }
  
  Future<void> _onSendMessage(SendMessage event, Emitter<ChatState> emit) async {
    try {
      await repository.sendMessage(
        roomId: event.roomId,
        content: event.content,
        type: event.type,
        mediaUrl: event.mediaUrl,
      );
    } catch (e) {
      emit(ChatError(message: e.toString()));
    }
  }
  
  void _onMessageReceived(MessageReceived event, Emitter<ChatState> emit) {
    if (state is RoomJoined) {
      final currentState = state as RoomJoined;
      final messages = List<ChatMessage>.from(currentState.messages);
      messages.insert(0, event.message);
      emit(currentState.copyWith(messages: messages));
    }
  }
  
  void _onRoomUpdated(RoomUpdated event, Emitter<ChatState> emit) {
    if (state is RoomsLoaded) {
      final currentState = state as RoomsLoaded;
      final rooms = List<ChatRoom>.from(currentState.rooms);
      final index = rooms.indexWhere((r) => r.id == event.room.id);
      if (index != -1) {
        rooms[index] = event.room;
      } else {
        rooms.insert(0, event.room);
      }
      emit(currentState.copyWith(rooms: rooms));
    }
  }
  
  void _onSendTypingIndicator(SendTypingIndicator event, Emitter<ChatState> emit) {
    repository.connect();
  }
  
  Future<void> _onMarkAsRead(MarkAsRead event, Emitter<ChatState> emit) async {
    try {
      await repository.markAsRead(event.roomId);
    } catch (e) {
      // Silently fail for read receipts
    }
  }
  
  @override
  Future<void> close() {
    repository.disconnect();
    return super.close();
  }
}
