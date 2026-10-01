import '../../domain/entities/event.dart';

class EventModel extends Event {
  const EventModel({
    required super.id,
    required super.organizerId,
    required super.title,
    super.description,
    super.details,
    super.settings,
    super.stats,
    super.status,
    super.createdAt,
    super.updatedAt,
  });
  
  factory EventModel.fromJson(Map<String, dynamic> json) {
    return EventModel(
      id: json['id'] ?? '',
      organizerId: json['organizerId'] ?? '',
      title: json['title'] ?? '',
      description: json['description'],
      details: json['details'] != null 
          ? EventDetailsModel.fromJson(json['details']) 
          : null,
      settings: json['settings'] != null 
          ? EventSettingsModel.fromJson(json['settings']) 
          : null,
      stats: json['stats'] != null 
          ? EventStatsModel.fromJson(json['stats']) 
          : null,
      status: _parseStatus(json['status']),
      createdAt: json['createdAt'] != null 
          ? DateTime.parse(json['createdAt']) 
          : null,
      updatedAt: json['updatedAt'] != null 
          ? DateTime.parse(json['updatedAt']) 
          : null,
    );
  }
  
  static EventStatus _parseStatus(String? status) {
    switch (status) {
      case 'PUBLISHED':
        return EventStatus.published;
      case 'CANCELLED':
        return EventStatus.cancelled;
      case 'COMPLETED':
        return EventStatus.completed;
      default:
        return EventStatus.draft;
    }
  }
  
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'organizerId': organizerId,
      'title': title,
      'description': description,
      'details': details != null ? EventDetailsModel.fromEntity(details!).toJson() : null,
      'settings': settings != null ? EventSettingsModel.fromEntity(settings!).toJson() : null,
      'stats': stats != null ? EventStatsModel.fromEntity(stats!).toJson() : null,
      'status': status.name.toUpperCase(),
      'createdAt': createdAt?.toIso8601String(),
      'updatedAt': updatedAt?.toIso8601String(),
    };
  }
}

class EventDetailsModel extends EventDetails {
  const EventDetailsModel({
    super.startDate,
    super.endDate,
    super.location,
    super.address,
    super.latitude,
    super.longitude,
    super.coverImages,
    super.tags,
    super.category,
  });
  
  factory EventDetailsModel.fromEntity(EventDetails entity) {
    if (entity is EventDetailsModel) return entity;
    return EventDetailsModel(
      startDate: entity.startDate,
      endDate: entity.endDate,
      location: entity.location,
      address: entity.address,
      latitude: entity.latitude,
      longitude: entity.longitude,
      coverImages: entity.coverImages,
      tags: entity.tags,
      category: entity.category,
    );
  }

  factory EventDetailsModel.fromJson(Map<String, dynamic> json) {
    return EventDetailsModel(
      startDate: json['startDate'] != null 
          ? DateTime.parse(json['startDate']) 
          : null,
      endDate: json['endDate'] != null 
          ? DateTime.parse(json['endDate']) 
          : null,
      location: json['location'],
      address: json['address'],
      latitude: json['latitude']?.toDouble(),
      longitude: json['longitude']?.toDouble(),
      coverImages: json['coverImages'] != null 
          ? List<String>.from(json['coverImages']) 
          : null,
      tags: json['tags'] != null 
          ? List<String>.from(json['tags']) 
          : null,
      category: json['category'],
    );
  }
  
  Map<String, dynamic> toJson() {
    return {
      'startDate': startDate?.toUtc().toIso8601String(),
      'endDate': endDate?.toUtc().toIso8601String(),
      'location': location,
      'address': address,
      'latitude': latitude,
      'longitude': longitude,
      'coverImages': coverImages,
      'tags': tags,
      'category': category,
    };
  }
}

class EventSettingsModel extends EventSettings {
  const EventSettingsModel({
    super.isPublic,
    super.requiresApproval,
    super.maxAttendees,
    super.allowGuestInvites,
    super.enableLiveStreaming,
    super.enableMosaic,
    super.hideAddressUntilAccepted,
  });
  
  factory EventSettingsModel.fromEntity(EventSettings entity) {
    if (entity is EventSettingsModel) return entity;
    return EventSettingsModel(
      isPublic: entity.isPublic,
      requiresApproval: entity.requiresApproval,
      maxAttendees: entity.maxAttendees,
      allowGuestInvites: entity.allowGuestInvites,
      enableLiveStreaming: entity.enableLiveStreaming,
      enableMosaic: entity.enableMosaic,
      hideAddressUntilAccepted: entity.hideAddressUntilAccepted,
    );
  }

  factory EventSettingsModel.fromJson(Map<String, dynamic> json) {
    return EventSettingsModel(
      isPublic: json['isPublic'] ?? true,
      requiresApproval: json['requiresApproval'] ?? false,
      maxAttendees: json['maxAttendees'] ?? 100,
      allowGuestInvites: json['allowGuestInvites'] ?? true,
      enableLiveStreaming: json['enableLiveStreaming'] ?? true,
      enableMosaic: json['enableMosaic'] ?? true,
      hideAddressUntilAccepted: json['hideAddressUntilAccepted'] ?? false,
    );
  }
  
  Map<String, dynamic> toJson() {
    return {
      'isPublic': isPublic,
      'requiresApproval': requiresApproval,
      'maxAttendees': maxAttendees,
      'allowGuestInvites': allowGuestInvites,
      'enableLiveStreaming': enableLiveStreaming,
      'enableMosaic': enableMosaic,
      'hideAddressUntilAccepted': hideAddressUntilAccepted,
    };
  }
}

class EventStatsModel extends EventStats {
  const EventStatsModel({
    super.totalInvited,
    super.totalAccepted,
    super.totalDeclined,
    super.totalMaybe,
    super.totalWaitlisted,
    super.totalCheckedIn,
    super.totalViewers,
  });
  
  factory EventStatsModel.fromEntity(EventStats entity) {
    if (entity is EventStatsModel) return entity;
    return EventStatsModel(
      totalInvited: entity.totalInvited,
      totalAccepted: entity.totalAccepted,
      totalDeclined: entity.totalDeclined,
      totalMaybe: entity.totalMaybe,
      totalWaitlisted: entity.totalWaitlisted,
      totalCheckedIn: entity.totalCheckedIn,
      totalViewers: entity.totalViewers,
    );
  }

  factory EventStatsModel.fromJson(Map<String, dynamic> json) {
    return EventStatsModel(
      totalInvited: json['totalInvited'] ?? 0,
      totalAccepted: json['totalAccepted'] ?? 0,
      totalDeclined: json['totalDeclined'] ?? 0,
      totalMaybe: json['totalMaybe'] ?? 0,
      totalWaitlisted: json['totalWaitlisted'] ?? 0,
      totalCheckedIn: json['totalCheckedIn'] ?? 0,
      totalViewers: json['totalViewers'] ?? 0,
    );
  }
  
  Map<String, dynamic> toJson() {
    return {
      'totalInvited': totalInvited,
      'totalAccepted': totalAccepted,
      'totalDeclined': totalDeclined,
      'totalMaybe': totalMaybe,
      'totalWaitlisted': totalWaitlisted,
      'totalCheckedIn': totalCheckedIn,
      'totalViewers': totalViewers,
    };
  }
}
