import 'package:flutter_test/flutter_test.dart';
import 'package:vito_app/features/events/data/models/attendee_model.dart';
import 'package:vito_app/features/events/data/models/invitation_model.dart';
import 'package:vito_app/features/events/data/models/invite_link_model.dart';
import 'package:vito_app/features/events/data/models/rsvp_model.dart';
import 'package:vito_app/features/events/domain/entities/invitation.dart';
import 'package:vito_app/features/events/domain/entities/rsvp.dart';

import 'fixture_loader.dart';

/// T015 (spec 003, Ola 1) — Serialización de RSVP, asistentes, invitaciones
/// y enlaces de invitación contra `docs/api/fixtures/events/`.
void main() {
  group('Rsvp', () {
    test('respondRsvp.accepted.200.json', () {
      final rsvp = RSVPModel.fromJson(
          loadFixtureObject('events/respondRsvp.accepted.200.json'));

      expect(rsvp.eventId, 'evt_01H8X2K4QJ');
      expect(rsvp.userId, 'usr_01H8X2K4QV');
      expect(rsvp.status, RSVPStatus.accepted);
      expect(rsvp.message, '¡Ahí estaré!');
      expect(rsvp.respondedAt?.toUtc(), DateTime.utc(2026, 10, 28, 2, 10));
      expect(rsvp.waitlistedAt, isNull);
      expect(rsvp.isWaitlisted, isFalse);
    });

    test('respondRsvp.waitlisted.200.json trae waitlistedAt', () {
      final rsvp = RSVPModel.fromJson(
          loadFixtureObject('events/respondRsvp.waitlisted.200.json'));

      expect(rsvp.status, RSVPStatus.waitlisted);
      expect(rsvp.isWaitlisted, isTrue);
      expect(rsvp.waitlistedAt?.toUtc(), DateTime.utc(2026, 10, 28, 2, 12));
    });

    test('sin QR ni guestsCount: toJson tiene las mismas claves que el fixture',
        () {
      final json = loadFixtureObject('events/respondRsvp.accepted.200.json');
      expect(RSVPModel.fromJson(json).toJson().keys.toSet(), json.keys.toSet());
    });

    test('el cuerpo enviado coincide con respondRsvp.request.json', () {
      final request = loadFixtureObject('events/respondRsvp.request.json');

      expect(
        respondRsvpRequestJson(
          status: RSVPStatus.accepted,
          message: '¡Ahí estaré!',
        ),
        request,
      );
      expect(
        respondRsvpRequestJson(status: RSVPStatus.declined, message: '  '),
        {'status': 'DECLINED'},
      );
    });

    test('PENDING y WAITLISTED no se pueden enviar', () {
      for (final status in [RSVPStatus.pending, RSVPStatus.waitlisted]) {
        expect(
          () => respondRsvpRequestJson(status: status),
          throwsArgumentError,
          reason: status.name,
        );
      }
    });

    test('un status desconocido cae en PENDING (CONVENTIONS §6)', () {
      expect(parseRsvpStatus('CHECKED_IN'), RSVPStatus.pending);
    });
  });

  group('Attendee', () {
    test('listEventAttendees.200.json es un array plano sin correo', () {
      final list = loadFixtureList('events/listEventAttendees.200.json');
      final attendees = list
          .map((e) => AttendeeModel.fromJson(e as Map<String, dynamic>))
          .toList();

      expect(attendees, hasLength(2));
      expect(attendees.first.displayName, 'Carolina Martínez');
      expect(attendees.first.status, RSVPStatus.accepted);
      expect(attendees.last.displayName, isNull);
      expect(attendees.last.status, RSVPStatus.waitlisted);
      for (final raw in list.cast<Map<String, dynamic>>()) {
        expect(raw.containsKey('email'), isFalse);
        expect(raw.containsKey('phone'), isFalse);
      }
    });
  });

  group('Invitation', () {
    test('listEventInvitations.200.json distingue app y enlace', () {
      final invitations = loadFixtureList('events/listEventInvitations.200.json')
          .map((e) => InvitationModel.fromJson(e as Map<String, dynamic>))
          .toList();

      expect(invitations.first.inviteLinkId, isNull);
      expect(invitations.first.viaInviteLink, isFalse);
      expect(invitations.first.status, InvitationStatus.pending);
      expect(invitations.last.inviteLinkId, 'inl_01HA1B2C3D');
      expect(invitations.last.viaInviteLink, isTrue);
      expect(invitations.last.status, InvitationStatus.accepted);
      expect(
        invitations.last.respondedAt?.toUtc(),
        DateTime.utc(2026, 10, 28, 2, 12),
      );
    });

    test('sendInvitation.201.json conserva sus claves al serializar', () {
      final json = loadFixtureObject('events/sendInvitation.201.json');
      expect(
        InvitationModel.fromJson(json).toJson().keys.toSet(),
        containsAll(json.keys),
      );
    });
  });

  group('InviteLink', () {
    test('createInviteLink.201.json', () {
      final json = loadFixtureObject('events/createInviteLink.201.json');
      final link = InviteLinkModel.fromJson(json);

      expect(link.id, 'inl_01HA1B2C3D');
      expect(link.token, hasLength(22));
      expect(link.url, endsWith('/i/${link.token}'));
      expect(link.maxUses, 50);
      expect(link.uses, 0);
      expect(link.revoked, isFalse);
      expect(link.expiresAt.toUtc(), DateTime.utc(2026, 11, 3, 15));
      expect(link.toJson().keys.toSet(), json.keys.toSet());
    });

    test('listInviteLinks.200.json: maxUses null es sin límite', () {
      final links = loadFixtureList('events/listInviteLinks.200.json')
          .map((e) => InviteLinkModel.fromJson(e as Map<String, dynamic>))
          .toList();

      expect(links.first.uses, 3);
      expect(links.last.maxUses, isNull);
      expect(links.last.isExhausted, isFalse);
      expect(links.last.revoked, isTrue);
      expect(links.last.isActive, isFalse);
    });

    test('el cuerpo enviado coincide con createInviteLink.request.json', () {
      expect(
        createInviteLinkRequestJson(expiresInDays: 7, maxUses: 50),
        loadFixtureObject('events/createInviteLink.request.json'),
      );
      expect(createInviteLinkRequestJson(), isEmpty);
    });

    test('getInviteLinkPreview.200.json no trae dirección ni coordenadas', () {
      final json = loadFixtureObject('events/getInviteLinkPreview.200.json');
      final preview = InviteLinkPreviewModel.fromJson(json);

      expect(preview.title, 'Cumple de Caro');
      expect(preview.organizerDisplayName, 'Bernardo');
      expect(preview.location, 'Cartagena de Indias');
      expect(preview.coverImage, isNull);
      expect(preview.startDate?.toUtc(), DateTime.utc(2026, 12, 26, 1));
      for (final key in const ['address', 'latitude', 'longitude']) {
        expect(json.containsKey(key), isFalse, reason: key);
      }
      expect(preview.toJson().keys.toSet(), json.keys.toSet());
    });
  });
}
