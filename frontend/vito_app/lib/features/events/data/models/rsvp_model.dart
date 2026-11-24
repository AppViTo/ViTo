import '../../domain/entities/rsvp.dart';

/// `Rsvp` del contrato (Ola 1): `eventId`, `userId`, `status`, `message`,
/// `respondedAt` y `waitlistedAt`. Fechas ISO-8601 UTC que se muestran en la
/// hora del dispositivo (Bug-B01).
class RSVPModel extends RSVP {
  const RSVPModel({
    required super.eventId,
    required super.userId,
    required super.status,
    super.message,
    super.respondedAt,
    super.waitlistedAt,
  });

  factory RSVPModel.fromJson(Map<String, dynamic> json) {
    return RSVPModel(
      eventId: json['eventId'] ?? '',
      userId: json['userId'] ?? '',
      status: parseRsvpStatus(json['status']),
      message: json['message'],
      respondedAt: _parseDate(json['respondedAt']),
      waitlistedAt: _parseDate(json['waitlistedAt']),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'eventId': eventId,
      'userId': userId,
      'status': rsvpStatusToJson(status),
      'message': message,
      'respondedAt': respondedAt?.toUtc().toIso8601String(),
      'waitlistedAt': waitlistedAt?.toUtc().toIso8601String(),
    };
  }

  static DateTime? _parseDate(dynamic value) =>
      value is String ? DateTime.parse(value).toLocal() : null;
}

/// Un valor fuera del enum de CONVENTIONS §6 cae en `PENDING`.
RSVPStatus parseRsvpStatus(String? status) {
  switch (status) {
    case 'ACCEPTED':
      return RSVPStatus.accepted;
    case 'DECLINED':
      return RSVPStatus.declined;
    case 'MAYBE':
      return RSVPStatus.maybe;
    case 'WAITLISTED':
      return RSVPStatus.waitlisted;
    default:
      return RSVPStatus.pending;
  }
}

String rsvpStatusToJson(RSVPStatus status) => status.name.toUpperCase();

/// Cuerpo de `respondRsvp` (`RespondRsvpRequest`): `status` y `message`
/// opcional de hasta 280 caracteres.
Map<String, dynamic> respondRsvpRequestJson({
  required RSVPStatus status,
  String? message,
}) {
  if (!respondableRsvpStatuses.contains(status)) {
    throw ArgumentError.value(
      status,
      'status',
      'Solo ACCEPTED, DECLINED o MAYBE se pueden enviar',
    );
  }
  return {
    'status': rsvpStatusToJson(status),
    if (message != null && message.trim().isNotEmpty) 'message': message.trim(),
  };
}
