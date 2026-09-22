import 'package:equatable/equatable.dart';

class CheckInEvent extends Equatable {
  final String eventId;
  final String userId;
  final String username;
  final String displayName;
  final String? avatar;
  final DateTime? checkedInAt;
  final String? location;
  final int totalCheckedIn;
  
  const CheckInEvent({
    required this.eventId,
    required this.userId,
    required this.username,
    required this.displayName,
    this.avatar,
    this.checkedInAt,
    this.location,
    this.totalCheckedIn = 0,
  });
  
  @override
  List<Object?> get props => [
        eventId,
        userId,
        username,
        displayName,
        avatar,
        checkedInAt,
        location,
        totalCheckedIn,
      ];
}

class CheckInStats extends Equatable {
  final String eventId;
  final int totalExpected;
  final int totalCheckedIn;
  final int totalPending;
  final int totalExpired;
  final int totalRevoked;
  final double checkInRate;
  
  const CheckInStats({
    required this.eventId,
    this.totalExpected = 0,
    this.totalCheckedIn = 0,
    this.totalPending = 0,
    this.totalExpired = 0,
    this.totalRevoked = 0,
    this.checkInRate = 0,
  });
  
  @override
  List<Object?> get props => [
        eventId,
        totalExpected,
        totalCheckedIn,
        totalPending,
        totalExpired,
        totalRevoked,
        checkInRate,
      ];
}
