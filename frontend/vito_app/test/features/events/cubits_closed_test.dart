import 'dart:async';
import 'dart:typed_data';

import 'package:dartz/dartz.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/features/events/domain/entities/event.dart';
import 'package:vito_app/features/events/domain/entities/invitation.dart';
import 'package:vito_app/features/events/domain/entities/invite_link.dart';
import 'package:vito_app/features/events/domain/usecases/accept_invite_link.dart';
import 'package:vito_app/features/events/domain/usecases/create_invite_link.dart';
import 'package:vito_app/features/events/domain/usecases/get_invite_link_preview.dart';
import 'package:vito_app/features/events/domain/usecases/list_invite_links.dart';
import 'package:vito_app/features/events/domain/usecases/revoke_invite_link.dart';
import 'package:vito_app/features/events/presentation/bloc/edit_event_cubit.dart';
import 'package:vito_app/features/events/presentation/bloc/invite_link_cubit.dart';
import 'package:vito_app/features/events/presentation/bloc/invite_links_cubit.dart';

import 'fakes.dart';

/// [FakeEventRepository] cuyas respuestas esperan a [gate], para cerrar el
/// cubit con la petición en curso.
class _GatedRepository extends FakeEventRepository {
  Completer<void>? gate;

  _GatedRepository({super.inviteLinkPreview});

  Future<T> _wait<T>(Future<T> Function() call) async {
    await gate?.future;
    return call();
  }

  @override
  Future<Either<Failure, InviteLinkPreview>> getInviteLinkPreview(
          String token) =>
      _wait(() => super.getInviteLinkPreview(token));

  @override
  Future<Either<Failure, Invitation>> acceptInviteLink(String token) =>
      _wait(() => super.acceptInviteLink(token));

  @override
  Future<Either<Failure, List<InviteLink>>> listInviteLinks({
    required String eventId,
    int page = 0,
    int size = 20,
  }) =>
      _wait(() => super.listInviteLinks(eventId: eventId, page: page, size: size));

  @override
  Future<Either<Failure, InviteLink>> createInviteLink({
    required String eventId,
    int? expiresInDays,
    int? maxUses,
  }) =>
      _wait(() => super.createInviteLink(
            eventId: eventId,
            expiresInDays: expiresInDays,
            maxUses: maxUses,
          ));

  @override
  Future<Either<Failure, void>> revokeInviteLink({
    required String eventId,
    required String linkId,
  }) =>
      _wait(() => super.revokeInviteLink(eventId: eventId, linkId: linkId));

  @override
  Future<Either<Failure, String>> uploadEventCover({
    required String eventId,
    required Uint8List bytes,
    required String fileName,
    required String mimeType,
  }) =>
      _wait(() => super.uploadEventCover(
            eventId: eventId,
            bytes: bytes,
            fileName: fileName,
            mimeType: mimeType,
          ));

  @override
  Future<Either<Failure, Event>> updateEvent({
    required Event original,
    required Event edited,
  }) =>
      _wait(() => super.updateEvent(original: original, edited: edited));
}

final _preview = InviteLinkPreview(
  eventId: 'evt_1',
  title: 'Cumple de Caro',
  organizerDisplayName: 'Bernardo',
  startDate: DateTime.utc(2026, 12, 26, 1),
  expiresAt: DateTime.utc(2026, 12, 3, 15),
);

const _event = Event(id: 'evt_1', organizerId: 'usr_1', title: 'Los 30 de Marta');

/// Ejecuta [action] con la respuesta retenida, cierra el cubit y suelta la
/// respuesta: el cubit no debe emitir después de `close` (Bad state).
Future<void> _closeMidway(
  _GatedRepository repository,
  Future<void> Function() action,
  Future<void> Function() close,
) async {
  repository.gate = Completer<void>();
  final pending = action();
  await close();
  repository.gate!.complete();
  await expectLater(pending, completes);
}

/// Los cubits de la Ola 1 comprueban `isClosed` tras cada `await` (revisión
/// de #330 a #334: cerrar la página con la petición en curso).
void main() {
  group('InviteLinkCubit', () {
    InviteLinkCubit build(_GatedRepository repository) => InviteLinkCubit(
          token: 'tok',
          getPreview: GetInviteLinkPreviewUseCase(repository),
          acceptInviteLink: AcceptInviteLinkUseCase(repository),
        );

    test('load', () async {
      final repository = _GatedRepository(inviteLinkPreview: _preview);
      final cubit = build(repository);
      await _closeMidway(repository, cubit.load, cubit.close);
    });

    test('join', () async {
      final repository = _GatedRepository(inviteLinkPreview: _preview);
      final cubit = build(repository);
      await cubit.load();
      await _closeMidway(repository, cubit.join, cubit.close);
    });
  });

  group('InviteLinksCubit', () {
    InviteLinksCubit build(_GatedRepository repository) => InviteLinksCubit(
          eventId: 'evt_1',
          listInviteLinks: ListInviteLinksUseCase(repository),
          createInviteLink: CreateInviteLinkUseCase(repository),
          revokeInviteLink: RevokeInviteLinkUseCase(repository),
        );

    test('load', () async {
      final repository = _GatedRepository();
      final cubit = build(repository);
      await _closeMidway(repository, cubit.load, cubit.close);
    });

    test('create', () async {
      final repository = _GatedRepository();
      final cubit = build(repository);
      await _closeMidway(repository, cubit.create, cubit.close);
    });

    test('revoke', () async {
      final repository = _GatedRepository();
      final cubit = build(repository);
      await _closeMidway(repository, () => cubit.revoke('inl_1'), cubit.close);
    });
  });

  group('EditEventCubit', () {
    test('uploadCover', () async {
      final repository = _GatedRepository();
      final cubit = EditEventCubit(repository: repository);
      await _closeMidway(
        repository,
        () => cubit.uploadCover(
          eventId: 'evt_1',
          bytes: Uint8List.fromList([1]),
          fileName: 'c.jpg',
          mimeType: 'image/jpeg',
        ),
        cubit.close,
      );
    });

    test('save', () async {
      final repository = _GatedRepository();
      final cubit = EditEventCubit(repository: repository);
      await _closeMidway(
        repository,
        () => cubit.save(original: _event, edited: _event),
        cubit.close,
      );
    });
  });
}
