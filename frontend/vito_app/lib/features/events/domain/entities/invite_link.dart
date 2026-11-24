import 'package:equatable/equatable.dart';

/// Enlace de invitación (`InviteLink` del contrato). Solo lo ve el
/// organizador: [token] es secreto y quien lo tenga puede unirse mientras el
/// enlace esté vigente.
class InviteLink extends Equatable {
  final String id;
  final String eventId;
  final String token;
  final String url;
  final DateTime expiresAt;

  /// `null` significa sin límite de usos.
  final int? maxUses;
  final int uses;
  final bool revoked;
  final DateTime createdAt;

  const InviteLink({
    required this.id,
    required this.eventId,
    required this.token,
    required this.url,
    required this.expiresAt,
    required this.uses,
    required this.revoked,
    required this.createdAt,
    this.maxUses,
  });

  bool get isExpired => DateTime.now().isAfter(expiresAt);

  bool get isExhausted => maxUses != null && uses >= maxUses!;

  /// Se puede compartir: ni revocado, ni vencido, ni sin usos.
  bool get isActive => !revoked && !isExpired && !isExhausted;

  @override
  List<Object?> get props => [
        id,
        eventId,
        token,
        url,
        expiresAt,
        maxUses,
        uses,
        revoked,
        createdAt,
      ];
}

/// Vista previa pública de un enlace (`InviteLinkPreview`). Nunca trae la
/// dirección exacta ni coordenadas.
class InviteLinkPreview extends Equatable {
  final String eventId;
  final String title;
  final DateTime? startDate;
  final DateTime? endDate;

  /// Barrio o nombre del lugar, sin dirección exacta.
  final String? location;
  final String? coverImage;
  final String? category;
  final String organizerDisplayName;
  final DateTime expiresAt;

  const InviteLinkPreview({
    required this.eventId,
    required this.title,
    required this.organizerDisplayName,
    required this.expiresAt,
    this.startDate,
    this.endDate,
    this.location,
    this.coverImage,
    this.category,
  });

  @override
  List<Object?> get props => [
        eventId,
        title,
        startDate,
        endDate,
        location,
        coverImage,
        category,
        organizerDisplayName,
        expiresAt,
      ];
}
