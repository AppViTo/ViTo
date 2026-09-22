import '../../domain/entities/checkin_event.dart';
import 'checkin_ticket_model.dart';

class CheckInEventModel extends CheckInEvent {
  const CheckInEventModel({
    required super.eventId,
    required super.userId,
    required super.username,
    required super.displayName,
    super.avatar,
    super.checkedInAt,
    super.location,
    super.totalCheckedIn,
  });
  
  factory CheckInEventModel.fromJson(Map<String, dynamic> json) {
    return CheckInEventModel(
      eventId: json['eventId'] ?? '',
      userId: json['userId'] ?? '',
      username: json['username'] ?? '',
      displayName: json['displayName'] ?? '',
      avatar: json['avatar'],
      checkedInAt: json['checkedInAt'] != null 
          ? DateTime.parse(json['checkedInAt']) 
          : null,
      location: json['location'],
      totalCheckedIn: json['totalCheckedIn'] ?? 0,
    );
  }
  
  Map<String, dynamic> toJson() {
    return {
      'eventId': eventId,
      'userId': userId,
      'username': username,
      'displayName': displayName,
      'avatar': avatar,
      'checkedInAt': checkedInAt?.toIso8601String(),
      'location': location,
      'totalCheckedIn': totalCheckedIn,
    };
  }
}

class CheckInResultModel {
  final bool success;
  final String message;
  final CheckInTicketModel? ticket;
  final CheckInEventModel? event;
  
  CheckInResultModel({
    required this.success,
    required this.message,
    this.ticket,
    this.event,
  });
  
  factory CheckInResultModel.fromJson(Map<String, dynamic> json) {
    return CheckInResultModel(
      success: json['success'] ?? false,
      message: json['message'] ?? '',
      ticket: json['ticket'] != null 
          ? CheckInTicketModel.fromJson(json['ticket']) 
          : null,
      event: json['event'] != null 
          ? CheckInEventModel.fromJson(json['event']) 
          : null,
    );
  }
}

class CheckInStatsModel extends CheckInStats {
  const CheckInStatsModel({
    required super.eventId,
    super.totalExpected,
    super.totalCheckedIn,
    super.totalPending,
    super.totalExpired,
    super.totalRevoked,
    super.checkInRate,
  });
  
  factory CheckInStatsModel.fromJson(Map<String, dynamic> json) {
    return CheckInStatsModel(
      eventId: json['eventId'] ?? '',
      totalExpected: json['totalExpected'] ?? 0,
      totalCheckedIn: json['totalCheckedIn'] ?? 0,
      totalPending: json['totalPending'] ?? 0,
      totalExpired: json['totalExpired'] ?? 0,
      totalRevoked: json['totalRevoked'] ?? 0,
      checkInRate: (json['checkInRate'] ?? 0).toDouble(),
    );
  }
  
  Map<String, dynamic> toJson() {
    return {
      'eventId': eventId,
      'totalExpected': totalExpected,
      'totalCheckedIn': totalCheckedIn,
      'totalPending': totalPending,
      'totalExpired': totalExpired,
      'totalRevoked': totalRevoked,
      'checkInRate': checkInRate,
    };
  }
}
