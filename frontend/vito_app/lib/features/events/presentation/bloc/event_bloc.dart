import 'dart:typed_data';

import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:equatable/equatable.dart';

import '../../domain/entities/event.dart';
import '../../domain/entities/rsvp.dart';
import '../../domain/entities/invitation.dart';
import '../../domain/repositories/event_repository.dart';

// Events
abstract class EventEvent extends Equatable {
  const EventEvent();
  
  @override
  List<Object?> get props => [];
}

class CreateEvent extends EventEvent {
  final String title;
  final String? description;
  final EventDetails? details;
  final EventSettings? settings;
  
  const CreateEvent({
    required this.title,
    this.description,
    this.details,
    this.settings,
  });
  
  @override
  List<Object?> get props => [title, description, details, settings];
}

class UpdateEvent extends EventEvent {
  final String eventId;
  final String? title;
  final String? description;
  final EventDetails? details;
  final EventSettings? settings;
  
  const UpdateEvent({
    required this.eventId,
    this.title,
    this.description,
    this.details,
    this.settings,
  });
  
  @override
  List<Object?> get props => [eventId, title, description, details, settings];
}

class CancelEvent extends EventEvent {
  final String eventId;
  
  const CancelEvent(this.eventId);
  
  @override
  List<Object?> get props => [eventId];
}

class LoadEvent extends EventEvent {
  final String eventId;
  
  const LoadEvent(this.eventId);
  
  @override
  List<Object?> get props => [eventId];
}

class LoadMyEvents extends EventEvent {}

class LoadUpcomingEvents extends EventEvent {}

class RespondToRSVP extends EventEvent {
  final String eventId;
  final RSVPStatus status;
  final String? message;
  final int guestsCount;
  
  const RespondToRSVP({
    required this.eventId,
    required this.status,
    this.message,
    this.guestsCount = 0,
  });
  
  @override
  List<Object?> get props => [eventId, status, message, guestsCount];
}

class LoadEventAttendees extends EventEvent {
  final String eventId;
  
  const LoadEventAttendees(this.eventId);
  
  @override
  List<Object?> get props => [eventId];
}

class SendInvitation extends EventEvent {
  final String eventId;
  final String inviteeId;
  final String? message;
  
  const SendInvitation({
    required this.eventId,
    required this.inviteeId,
    this.message,
  });
  
  @override
  List<Object?> get props => [eventId, inviteeId, message];
}

class LoadEventInvitations extends EventEvent {
  final String eventId;
  
  const LoadEventInvitations(this.eventId);
  
  @override
  List<Object?> get props => [eventId];
}

class RespondToInvitation extends EventEvent {
  final String eventId;
  final InvitationStatus status;
  
  const RespondToInvitation({
    required this.eventId,
    required this.status,
  });
  
  @override
  List<Object?> get props => [eventId, status];
}

class CheckIn extends EventEvent {
  final String eventId;
  final String qrCode;
  
  const CheckIn({
    required this.eventId,
    required this.qrCode,
  });
  
  @override
  List<Object?> get props => [eventId, qrCode];
}

class GenerateCheckInQR extends EventEvent {
  final String eventId;
  
  const GenerateCheckInQR(this.eventId);
  
  @override
  List<Object?> get props => [eventId];
}

class UploadEventCover extends EventEvent {
  final String eventId;
  final Uint8List bytes;
  final String fileName;
  final String mimeType;
  
  const UploadEventCover({
    required this.eventId,
    required this.bytes,
    required this.fileName,
    required this.mimeType,
  });
  
  @override
  List<Object?> get props => [eventId, bytes, fileName, mimeType];
}

// States
abstract class EventState extends Equatable {
  const EventState();
  
  @override
  List<Object?> get props => [];
}

class EventInitial extends EventState {}

class EventLoading extends EventState {}

class EventCreated extends EventState {
  final Event event;
  
  const EventCreated(this.event);
  
  @override
  List<Object?> get props => [event];
}

class EventUpdated extends EventState {
  final Event event;
  
  const EventUpdated(this.event);
  
  @override
  List<Object?> get props => [event];
}

class EventLoaded extends EventState {
  final Event event;
  
  const EventLoaded(this.event);
  
  @override
  List<Object?> get props => [event];
}

class MyEventsLoaded extends EventState {
  final List<Event> events;
  
  const MyEventsLoaded(this.events);
  
  @override
  List<Object?> get props => [events];
}

class UpcomingEventsLoaded extends EventState {
  final List<Event> events;
  
  const UpcomingEventsLoaded(this.events);
  
  @override
  List<Object?> get props => [events];
}

class RSVPSubmitted extends EventState {
  final RSVP rsvp;
  
  const RSVPSubmitted(this.rsvp);
  
  @override
  List<Object?> get props => [rsvp];
}

class EventAttendeesLoaded extends EventState {
  final List<RSVP> attendees;
  
  const EventAttendeesLoaded(this.attendees);
  
  @override
  List<Object?> get props => [attendees];
}

class InvitationSent extends EventState {
  final Invitation invitation;
  
  const InvitationSent(this.invitation);
  
  @override
  List<Object?> get props => [invitation];
}

class EventInvitationsLoaded extends EventState {
  final List<Invitation> invitations;
  
  const EventInvitationsLoaded(this.invitations);
  
  @override
  List<Object?> get props => [invitations];
}

class InvitationResponded extends EventState {}

class CheckInSuccess extends EventState {
  final RSVP rsvp;
  
  const CheckInSuccess(this.rsvp);
  
  @override
  List<Object?> get props => [rsvp];
}

class CheckInQRGenerated extends EventState {
  final String qrCode;
  
  const CheckInQRGenerated(this.qrCode);
  
  @override
  List<Object?> get props => [qrCode];
}

class EventCoverUploading extends EventState {}

class EventCoverUploaded extends EventState {
  final String url;
  
  const EventCoverUploaded(this.url);
  
  @override
  List<Object?> get props => [url];
}

class EventCoverUploadFailed extends EventState {
  final String message;
  
  const EventCoverUploadFailed(this.message);
  
  @override
  List<Object?> get props => [message];
}

class EventError extends EventState {
  final String message;
  
  const EventError(this.message);
  
  @override
  List<Object?> get props => [message];
}

// BLoC
class EventBloc extends Bloc<EventEvent, EventState> {
  final EventRepository eventRepository;
  
  EventBloc({required this.eventRepository}) : super(EventInitial()) {
    on<CreateEvent>(_onCreateEvent);
    on<UpdateEvent>(_onUpdateEvent);
    on<CancelEvent>(_onCancelEvent);
    on<LoadEvent>(_onLoadEvent);
    on<LoadMyEvents>(_onLoadMyEvents);
    on<LoadUpcomingEvents>(_onLoadUpcomingEvents);
    on<RespondToRSVP>(_onRespondToRSVP);
    on<LoadEventAttendees>(_onLoadEventAttendees);
    on<SendInvitation>(_onSendInvitation);
    on<LoadEventInvitations>(_onLoadEventInvitations);
    on<RespondToInvitation>(_onRespondToInvitation);
    on<CheckIn>(_onCheckIn);
    on<GenerateCheckInQR>(_onGenerateCheckInQR);
    on<UploadEventCover>(_onUploadEventCover);
  }
  
  Future<void> _onCreateEvent(CreateEvent event, Emitter<EventState> emit) async {
    emit(EventLoading());
    
    final result = await eventRepository.createEvent(
      title: event.title,
      description: event.description,
      details: event.details,
      settings: event.settings,
    );
    
    result.fold(
      (failure) => emit(EventError(failure.message)),
      (event) => emit(EventCreated(event)),
    );
  }
  
  Future<void> _onUpdateEvent(UpdateEvent event, Emitter<EventState> emit) async {
    emit(EventLoading());
    
    final result = await eventRepository.updateEvent(
      eventId: event.eventId,
      title: event.title,
      description: event.description,
      details: event.details,
      settings: event.settings,
    );
    
    result.fold(
      (failure) => emit(EventError(failure.message)),
      (event) => emit(EventUpdated(event)),
    );
  }
  
  Future<void> _onCancelEvent(CancelEvent event, Emitter<EventState> emit) async {
    emit(EventLoading());
    
    final result = await eventRepository.cancelEvent(event.eventId);
    
    result.fold(
      (failure) => emit(EventError(failure.message)),
      (_) => emit(EventInitial()),
    );
  }
  
  Future<void> _onLoadEvent(LoadEvent event, Emitter<EventState> emit) async {
    emit(EventLoading());
    
    final result = await eventRepository.getEvent(event.eventId);
    
    result.fold(
      (failure) => emit(EventError(failure.message)),
      (event) => emit(EventLoaded(event)),
    );
  }
  
  Future<void> _onLoadMyEvents(LoadMyEvents event, Emitter<EventState> emit) async {
    emit(EventLoading());
    
    final result = await eventRepository.getMyEvents();
    
    result.fold(
      (failure) => emit(EventError(failure.message)),
      (events) => emit(MyEventsLoaded(events)),
    );
  }
  
  Future<void> _onLoadUpcomingEvents(LoadUpcomingEvents event, Emitter<EventState> emit) async {
    emit(EventLoading());
    
    final result = await eventRepository.getUpcomingEvents();
    
    result.fold(
      (failure) => emit(EventError(failure.message)),
      (events) => emit(UpcomingEventsLoaded(events)),
    );
  }
  
  Future<void> _onRespondToRSVP(RespondToRSVP event, Emitter<EventState> emit) async {
    emit(EventLoading());
    
    final result = await eventRepository.respondToRSVP(
      eventId: event.eventId,
      status: event.status,
      message: event.message,
      guestsCount: event.guestsCount,
    );
    
    result.fold(
      (failure) => emit(EventError(failure.message)),
      (rsvp) => emit(RSVPSubmitted(rsvp)),
    );
  }
  
  Future<void> _onLoadEventAttendees(LoadEventAttendees event, Emitter<EventState> emit) async {
    emit(EventLoading());
    
    final result = await eventRepository.getEventAttendees(eventId: event.eventId);
    
    result.fold(
      (failure) => emit(EventError(failure.message)),
      (attendees) => emit(EventAttendeesLoaded(attendees)),
    );
  }
  
  Future<void> _onSendInvitation(SendInvitation event, Emitter<EventState> emit) async {
    emit(EventLoading());
    
    final result = await eventRepository.sendInvitation(
      eventId: event.eventId,
      inviteeId: event.inviteeId,
      message: event.message,
    );
    
    result.fold(
      (failure) => emit(EventError(failure.message)),
      (invitation) => emit(InvitationSent(invitation)),
    );
  }
  
  Future<void> _onLoadEventInvitations(LoadEventInvitations event, Emitter<EventState> emit) async {
    emit(EventLoading());
    
    final result = await eventRepository.getEventInvitations(eventId: event.eventId);
    
    result.fold(
      (failure) => emit(EventError(failure.message)),
      (invitations) => emit(EventInvitationsLoaded(invitations)),
    );
  }
  
  Future<void> _onRespondToInvitation(RespondToInvitation event, Emitter<EventState> emit) async {
    emit(EventLoading());
    
    final result = await eventRepository.respondToInvitation(
      eventId: event.eventId,
      status: event.status,
    );
    
    result.fold(
      (failure) => emit(EventError(failure.message)),
      (_) => emit(InvitationResponded()),
    );
  }
  
  Future<void> _onCheckIn(CheckIn event, Emitter<EventState> emit) async {
    emit(EventLoading());
    
    final result = await eventRepository.checkIn(
      eventId: event.eventId,
      qrCode: event.qrCode,
    );
    
    result.fold(
      (failure) => emit(EventError(failure.message)),
      (rsvp) => emit(CheckInSuccess(rsvp)),
    );
  }
  
  Future<void> _onGenerateCheckInQR(GenerateCheckInQR event, Emitter<EventState> emit) async {
    emit(EventLoading());
    
    final result = await eventRepository.generateCheckInQR(event.eventId);
    
    result.fold(
      (failure) => emit(EventError(failure.message)),
      (qrCode) => emit(CheckInQRGenerated(qrCode)),
    );
  }
  
  Future<void> _onUploadEventCover(UploadEventCover event, Emitter<EventState> emit) async {
    emit(EventCoverUploading());
    
    final result = await eventRepository.uploadEventCover(
      eventId: event.eventId,
      bytes: event.bytes,
      fileName: event.fileName,
      mimeType: event.mimeType,
    );
    
    result.fold(
      (failure) => emit(EventCoverUploadFailed(failure.message)),
      (url) => emit(EventCoverUploaded(url)),
    );
  }
}
