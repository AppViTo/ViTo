import 'package:equatable/equatable.dart';

import 'rsvp.dart';

/// Asistente de un evento (`Attendee` del contrato). Solo lo ve el
/// organizador; nunca incluye correo ni teléfono.
class Attendee extends Equatable {
  final String userId;
  final String? displayName;
  final RSVPStatus status;
  final DateTime? respondedAt;

  const Attendee({
    required this.userId,
    required this.status,
    this.displayName,
    this.respondedAt,
  });

  @override
  List<Object?> get props => [userId, displayName, status, respondedAt];
}
