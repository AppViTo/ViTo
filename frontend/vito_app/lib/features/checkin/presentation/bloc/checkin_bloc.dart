import 'dart:async';

import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:equatable/equatable.dart';

import '../../domain/entities/checkin_ticket.dart';
import '../../domain/entities/checkin_event.dart';
import '../../domain/repositories/checkin_repository.dart';

// Events
abstract class CheckInBlocEvent extends Equatable {
  const CheckInBlocEvent();
  
  @override
  List<Object?> get props => [];
}

class GenerateTicket extends CheckInBlocEvent {
  final String eventId;
  
  const GenerateTicket(this.eventId);
  
  @override
  List<Object?> get props => [eventId];
}

class ValidateCheckIn extends CheckInBlocEvent {
  final String qrCode;
  final String eventId;
  final String? location;
  final double? latitude;
  final double? longitude;
  
  const ValidateCheckIn({
    required this.qrCode,
    required this.eventId,
    this.location,
    this.latitude,
    this.longitude,
  });
  
  @override
  List<Object?> get props => [qrCode, eventId, location, latitude, longitude];
}

class LoadTicket extends CheckInBlocEvent {
  final String ticketId;
  
  const LoadTicket(this.ticketId);
  
  @override
  List<Object?> get props => [ticketId];
}

class LoadEventTickets extends CheckInBlocEvent {
  final String eventId;
  
  const LoadEventTickets(this.eventId);
  
  @override
  List<Object?> get props => [eventId];
}

class LoadRecentCheckIns extends CheckInBlocEvent {
  final String eventId;
  
  const LoadRecentCheckIns(this.eventId);
  
  @override
  List<Object?> get props => [eventId];
}

class LoadEventStats extends CheckInBlocEvent {
  final String eventId;
  
  const LoadEventStats(this.eventId);
  
  @override
  List<Object?> get props => [eventId];
}

class RevokeTicket extends CheckInBlocEvent {
  final String ticketId;
  
  const RevokeTicket(this.ticketId);
  
  @override
  List<Object?> get props => [ticketId];
}

class ConnectToWebSocket extends CheckInBlocEvent {
  final String eventId;
  
  const ConnectToWebSocket({
    required this.eventId,
  });
  
  @override
  List<Object?> get props => [eventId];
}

class DisconnectFromWebSocket extends CheckInBlocEvent {}

class CheckInEventReceived extends CheckInBlocEvent {
  final CheckInEvent checkInEvent;
  
  const CheckInEventReceived(this.checkInEvent);
  
  @override
  List<Object?> get props => [checkInEvent];
}

class StatsUpdated extends CheckInBlocEvent {
  final CheckInStats stats;
  
  const StatsUpdated(this.stats);
  
  @override
  List<Object?> get props => [stats];
}

// States
abstract class CheckInState extends Equatable {
  const CheckInState();
  
  @override
  List<Object?> get props => [];
}

class CheckInInitial extends CheckInState {}

class CheckInLoading extends CheckInState {}

class TicketGenerated extends CheckInState {
  final CheckInTicket ticket;
  
  const TicketGenerated(this.ticket);
  
  @override
  List<Object?> get props => [ticket];
}

class CheckInSuccess extends CheckInState {
  final CheckInResult result;
  
  const CheckInSuccess(this.result);
  
  @override
  List<Object?> get props => [result];
}

class TicketLoaded extends CheckInState {
  final CheckInTicket ticket;
  
  const TicketLoaded(this.ticket);
  
  @override
  List<Object?> get props => [ticket];
}

class EventTicketsLoaded extends CheckInState {
  final List<CheckInTicket> tickets;
  
  const EventTicketsLoaded(this.tickets);
  
  @override
  List<Object?> get props => [tickets];
}

class RecentCheckInsLoaded extends CheckInState {
  final List<CheckInEvent> checkIns;
  
  const RecentCheckInsLoaded(this.checkIns);
  
  @override
  List<Object?> get props => [checkIns];
}

class EventStatsLoaded extends CheckInState {
  final CheckInStats stats;
  
  const EventStatsLoaded(this.stats);
  
  @override
  List<Object?> get props => [stats];
}

class TicketRevoked extends CheckInState {}

class WebSocketConnected extends CheckInState {}

class WebSocketDisconnected extends CheckInState {}

class RealTimeCheckIn extends CheckInState {
  final CheckInEvent event;
  
  const RealTimeCheckIn(this.event);
  
  @override
  List<Object?> get props => [event];
}

class RealTimeStatsUpdate extends CheckInState {
  final CheckInStats stats;
  
  const RealTimeStatsUpdate(this.stats);
  
  @override
  List<Object?> get props => [stats];
}

class CheckInError extends CheckInState {
  final String message;
  
  const CheckInError(this.message);
  
  @override
  List<Object?> get props => [message];
}

// BLoC
class CheckInBloc extends Bloc<CheckInBlocEvent, CheckInState> {
  final CheckInRepository repository;
  StreamSubscription? _checkInSubscription;
  StreamSubscription? _statsSubscription;
  
  CheckInBloc({required this.repository}) : super(CheckInInitial()) {
    on<GenerateTicket>(_onGenerateTicket);
    on<ValidateCheckIn>(_onValidateCheckIn);
    on<LoadTicket>(_onLoadTicket);
    on<LoadEventTickets>(_onLoadEventTickets);
    on<LoadRecentCheckIns>(_onLoadRecentCheckIns);
    on<LoadEventStats>(_onLoadEventStats);
    on<RevokeTicket>(_onRevokeTicket);
    on<ConnectToWebSocket>(_onConnectToWebSocket);
    on<DisconnectFromWebSocket>(_onDisconnectFromWebSocket);
    on<CheckInEventReceived>(_onCheckInEventReceived);
    on<StatsUpdated>(_onStatsUpdated);
  }
  
  Future<void> _onGenerateTicket(GenerateTicket event, Emitter<CheckInState> emit) async {
    emit(CheckInLoading());
    
    final result = await repository.generateTicket(event.eventId);
    
    result.fold(
      (failure) => emit(CheckInError(failure.message)),
      (ticket) => emit(TicketGenerated(ticket)),
    );
  }
  
  Future<void> _onValidateCheckIn(ValidateCheckIn event, Emitter<CheckInState> emit) async {
    emit(CheckInLoading());
    
    final result = await repository.validateAndCheckIn(
      qrCode: event.qrCode,
      eventId: event.eventId,
      location: event.location,
      latitude: event.latitude,
      longitude: event.longitude,
    );
    
    result.fold(
      (failure) => emit(CheckInError(failure.message)),
      (checkInResult) => emit(CheckInSuccess(checkInResult)),
    );
  }
  
  Future<void> _onLoadTicket(LoadTicket event, Emitter<CheckInState> emit) async {
    emit(CheckInLoading());
    
    final result = await repository.getTicket(event.ticketId);
    
    result.fold(
      (failure) => emit(CheckInError(failure.message)),
      (ticket) => emit(TicketLoaded(ticket)),
    );
  }
  
  Future<void> _onLoadEventTickets(LoadEventTickets event, Emitter<CheckInState> emit) async {
    emit(CheckInLoading());
    
    final result = await repository.getEventTickets(eventId: event.eventId);
    
    result.fold(
      (failure) => emit(CheckInError(failure.message)),
      (tickets) => emit(EventTicketsLoaded(tickets)),
    );
  }
  
  Future<void> _onLoadRecentCheckIns(LoadRecentCheckIns event, Emitter<CheckInState> emit) async {
    emit(CheckInLoading());
    
    final result = await repository.getRecentCheckIns(eventId: event.eventId);
    
    result.fold(
      (failure) => emit(CheckInError(failure.message)),
      (checkIns) => emit(RecentCheckInsLoaded(checkIns)),
    );
  }
  
  Future<void> _onLoadEventStats(LoadEventStats event, Emitter<CheckInState> emit) async {
    emit(CheckInLoading());
    
    final result = await repository.getEventStats(event.eventId);
    
    result.fold(
      (failure) => emit(CheckInError(failure.message)),
      (stats) => emit(EventStatsLoaded(stats)),
    );
  }
  
  Future<void> _onRevokeTicket(RevokeTicket event, Emitter<CheckInState> emit) async {
    emit(CheckInLoading());
    
    final result = await repository.revokeTicket(event.ticketId);
    
    result.fold(
      (failure) => emit(CheckInError(failure.message)),
      (_) => emit(TicketRevoked()),
    );
  }
  
  void _onConnectToWebSocket(ConnectToWebSocket event, Emitter<CheckInState> emit) {
    repository.connectToWebSocket(event.eventId);
    
    _checkInSubscription?.cancel();
    _statsSubscription?.cancel();
    
    _checkInSubscription = repository.checkInStream.listen(
      (checkInEvent) {
        add(CheckInEventReceived(checkInEvent));
      },
    );
    
    _statsSubscription = repository.statsStream.listen(
      (stats) {
        add(StatsUpdated(stats));
      },
    );
    
    emit(WebSocketConnected());
  }
  
  void _onDisconnectFromWebSocket(DisconnectFromWebSocket event, Emitter<CheckInState> emit) {
    repository.disconnectFromWebSocket();
    
    _checkInSubscription?.cancel();
    _statsSubscription?.cancel();
    
    emit(WebSocketDisconnected());
  }
  
  void _onCheckInEventReceived(CheckInEventReceived event, Emitter<CheckInState> emit) {
    emit(RealTimeCheckIn(event.checkInEvent));
  }
  
  void _onStatsUpdated(StatsUpdated event, Emitter<CheckInState> emit) {
    emit(RealTimeStatsUpdate(event.stats));
  }
  
  @override
  Future<void> close() {
    _checkInSubscription?.cancel();
    _statsSubscription?.cancel();
    repository.disconnectFromWebSocket();
    return super.close();
  }
}
