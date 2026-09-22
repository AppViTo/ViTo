import 'package:equatable/equatable.dart';

class Event extends Equatable {
  final String id;
  final String organizerId;
  final String title;
  final String? description;
  final EventDetails? details;
  final EventSettings? settings;
  final EventStats? stats;
  final EventStatus status;
  final DateTime? createdAt;
  final DateTime? updatedAt;
  
  const Event({
    required this.id,
    required this.organizerId,
    required this.title,
    this.description,
    this.details,
    this.settings,
    this.stats,
    this.status = EventStatus.draft,
    this.createdAt,
    this.updatedAt,
  });
  
  @override
  List<Object?> get props => [
        id,
        organizerId,
        title,
        description,
        details,
        settings,
        stats,
        status,
        createdAt,
        updatedAt,
      ];
}

enum EventStatus {
  draft,
  published,
  cancelled,
  completed,
}

class EventDetails extends Equatable {
  final DateTime? startDate;
  final DateTime? endDate;
  final String? location;
  final String? address;
  final double? latitude;
  final double? longitude;
  final List<String>? coverImages;
  final List<String>? tags;
  final String? category;
  
  const EventDetails({
    this.startDate,
    this.endDate,
    this.location,
    this.address,
    this.latitude,
    this.longitude,
    this.coverImages,
    this.tags,
    this.category,
  });
  
  @override
  List<Object?> get props => [
        startDate,
        endDate,
        location,
        address,
        latitude,
        longitude,
        coverImages,
        tags,
        category,
      ];
}

class EventSettings extends Equatable {
  final bool isPublic;
  final bool requiresApproval;
  final int maxAttendees;
  final bool allowGuestInvites;
  final bool enableLiveStreaming;
  final bool enableMosaic;
  
  const EventSettings({
    this.isPublic = true,
    this.requiresApproval = false,
    this.maxAttendees = 100,
    this.allowGuestInvites = true,
    this.enableLiveStreaming = true,
    this.enableMosaic = true,
  });
  
  @override
  List<Object?> get props => [
        isPublic,
        requiresApproval,
        maxAttendees,
        allowGuestInvites,
        enableLiveStreaming,
        enableMosaic,
      ];
}

class EventStats extends Equatable {
  final int totalInvited;
  final int totalAccepted;
  final int totalDeclined;
  final int totalMaybe;
  final int totalWaitlisted;
  final int totalCheckedIn;
  final int totalViewers;
  
  const EventStats({
    this.totalInvited = 0,
    this.totalAccepted = 0,
    this.totalDeclined = 0,
    this.totalMaybe = 0,
    this.totalWaitlisted = 0,
    this.totalCheckedIn = 0,
    this.totalViewers = 0,
  });
  
  @override
  List<Object?> get props => [
        totalInvited,
        totalAccepted,
        totalDeclined,
        totalMaybe,
        totalWaitlisted,
        totalCheckedIn,
        totalViewers,
      ];
}
