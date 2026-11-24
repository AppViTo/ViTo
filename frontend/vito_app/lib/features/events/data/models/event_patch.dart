import 'dart:convert';

import '../../domain/entities/event.dart';
import 'event_model.dart';

/// Cuerpo de `updateEvent` (`PATCH /events/{eventId}`,
/// `application/merge-patch+json`, T048): solo lo que cambió entre
/// [original] y [edited].
///
/// - `title` y `description` van en la raíz; un `description` vaciado se
///   envía como `null` para borrarlo (RFC 7396).
/// - En `details` y `settings` solo van las claves que cambiaron, dentro de su
///   objeto; el backend mezcla el resto. Ahí nunca va `null`: el contrato
///   define esas claves sin `nullable` (p. ej. `location` y `address` son
///   `string`), así que una clave que quedó sin valor se omite. Para vaciar
///   el lugar o la dirección, el formulario envía `""`.
/// - `id`, `organizerId`, `status`, `stats` y `createdAt` nunca se envían
///   (responden `400`).
///
/// Si nada cambió devuelve un mapa vacío: el contrato pide al menos una
/// propiedad, así que no se llama al backend.
Map<String, dynamic> eventPatchJson(Event original, Event edited) {
  final patch = <String, dynamic>{};

  if (edited.title != original.title) patch['title'] = edited.title;
  if (edited.description != original.description) {
    patch['description'] = edited.description;
  }

  final details = _changedKeys(
    EventDetailsModel.fromEntity(original.details ?? const EventDetails())
        .toJson(),
    EventDetailsModel.fromEntity(edited.details ?? const EventDetails())
        .toJson(),
  );
  if (details.isNotEmpty) patch['details'] = details;

  final settings = _changedKeys(
    EventSettingsModel.fromEntity(original.settings ?? const EventSettings())
        .toJson(),
    EventSettingsModel.fromEntity(edited.settings ?? const EventSettings())
        .toJson(),
  );
  if (settings.isNotEmpty) patch['settings'] = settings;

  return patch;
}

Map<String, dynamic> _changedKeys(
  Map<String, dynamic> before,
  Map<String, dynamic> after,
) {
  final changed = <String, dynamic>{};
  for (final key in after.keys) {
    if (after[key] == null) continue;
    if (jsonEncode(before[key]) != jsonEncode(after[key])) {
      changed[key] = after[key];
    }
  }
  return changed;
}
