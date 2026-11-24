import '../../domain/entities/invite_link.dart';

DateTime _date(dynamic value) => DateTime.parse(value as String).toLocal();

DateTime? _optionalDate(dynamic value) =>
    value is String ? DateTime.parse(value).toLocal() : null;

/// `InviteLink` del contrato (Ola 1).
class InviteLinkModel extends InviteLink {
  const InviteLinkModel({
    required super.id,
    required super.eventId,
    required super.token,
    required super.url,
    required super.expiresAt,
    required super.uses,
    required super.revoked,
    required super.createdAt,
    super.maxUses,
  });

  factory InviteLinkModel.fromJson(Map<String, dynamic> json) {
    return InviteLinkModel(
      id: json['id'] as String,
      eventId: json['eventId'] as String,
      token: json['token'] as String,
      url: json['url'] as String,
      expiresAt: _date(json['expiresAt']),
      maxUses: json['maxUses'] as int?,
      uses: (json['uses'] as int?) ?? 0,
      revoked: (json['revoked'] as bool?) ?? false,
      createdAt: _date(json['createdAt']),
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'eventId': eventId,
        'token': token,
        'url': url,
        'expiresAt': expiresAt.toUtc().toIso8601String(),
        'maxUses': maxUses,
        'uses': uses,
        'revoked': revoked,
        'createdAt': createdAt.toUtc().toIso8601String(),
      };
}

/// Cuerpo de `createInviteLink` (`CreateInviteLinkRequest`): `expiresInDays`
/// de 1 a 30 (7 por defecto) y `maxUses` de 1 a 1000 o `null` sin límite.
Map<String, dynamic> createInviteLinkRequestJson({
  int? expiresInDays,
  int? maxUses,
}) =>
    {
      if (expiresInDays != null) 'expiresInDays': expiresInDays,
      if (maxUses != null) 'maxUses': maxUses,
    };

/// `InviteLinkPreview` del contrato (Ola 1).
class InviteLinkPreviewModel extends InviteLinkPreview {
  const InviteLinkPreviewModel({
    required super.eventId,
    required super.title,
    required super.organizerDisplayName,
    required super.expiresAt,
    super.startDate,
    super.endDate,
    super.location,
    super.coverImage,
    super.category,
  });

  factory InviteLinkPreviewModel.fromJson(Map<String, dynamic> json) {
    return InviteLinkPreviewModel(
      eventId: json['eventId'] as String,
      title: json['title'] as String,
      startDate: _optionalDate(json['startDate']),
      endDate: _optionalDate(json['endDate']),
      location: json['location'] as String?,
      coverImage: json['coverImage'] as String?,
      category: json['category'] as String?,
      organizerDisplayName: json['organizerDisplayName'] as String,
      expiresAt: _date(json['expiresAt']),
    );
  }

  Map<String, dynamic> toJson() => {
        'eventId': eventId,
        'title': title,
        'startDate': startDate?.toUtc().toIso8601String(),
        'endDate': endDate?.toUtc().toIso8601String(),
        'location': location,
        'coverImage': coverImage,
        'category': category,
        'organizerDisplayName': organizerDisplayName,
        'expiresAt': expiresAt.toUtc().toIso8601String(),
      };
}
