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
      'details': details != null ? (details as EventDetailsModel).toJson() : null,
      'settings': settings != null ? (settings as EventSettingsModel).toJson() : null,
      'stats': stats != null ? (stats as EventStatsModel).toJson() : null,
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
      'startDate': startDate?.toIso8601String(),
      'endDate': endDate?.toIso8601String(),
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
  });
  
  factory EventSettingsModel.fromJson(Map<String, dynamic> json) {
    return EventSettingsModel(
      isPublic: json['isPublic'] ?? true,
      requiresApproval: json['requiresApproval'] ?? false,
      maxAttendees: json['maxAttendees'] ?? 100,
      allowGuestInvites: json['allowGuestInvites'] ?? true,
      enableLiveStreaming: json['enableLiveStreaming'] ?? true,
      enableMosaic: json['enableMosaic'] ?? true,
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
