import 'dart:typed_data';

import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../domain/entities/event.dart';
import '../../domain/repositories/event_repository.dart';
import '../../domain/usecases/update_event.dart';

enum EditEventStatus { editing, saving, saved }

/// Edición del evento por su organizador (T049). Tiene su propio estado para
/// no reemplazar el del detalle, que sigue debajo en la pila.
class EditEventState extends Equatable {
  final EditEventStatus status;

  /// El evento guardado, cuando [status] es `saved`.
  final Event? saved;

  /// URL de la portada recién subida, si se cambió.
  final String? coverUrl;
  final bool uploadingCover;

  /// Mensaje de la última acción fallida (el del backend en 400 y 409).
  final String? error;

  const EditEventState({
    this.status = EditEventStatus.editing,
    this.saved,
    this.coverUrl,
    this.uploadingCover = false,
    this.error,
  });

  EditEventState copyWith({
    EditEventStatus? status,
    Event? saved,
    String? coverUrl,
    bool? uploadingCover,
    String? error,
  }) =>
      EditEventState(
        status: status ?? this.status,
        saved: saved ?? this.saved,
        coverUrl: coverUrl ?? this.coverUrl,
        uploadingCover: uploadingCover ?? this.uploadingCover,
        error: error,
      );

  @override
  List<Object?> get props => [status, saved, coverUrl, uploadingCover, error];
}

class EditEventCubit extends Cubit<EditEventState> {
  final EventRepository repository;
  final UpdateEventUseCase updateEvent;

  EditEventCubit({required this.repository})
      : updateEvent = UpdateEventUseCase(repository),
        super(const EditEventState());

  /// Sube la nueva portada a media-service; se guarda con el resto al
  /// pulsar "Guardar cambios".
  Future<void> uploadCover({
    required String eventId,
    required Uint8List bytes,
    required String fileName,
    required String mimeType,
  }) async {
    emit(state.copyWith(uploadingCover: true));
    final result = await repository.uploadEventCover(
      eventId: eventId,
      bytes: bytes,
      fileName: fileName,
      mimeType: mimeType,
    );
    result.fold(
      (failure) =>
          emit(state.copyWith(uploadingCover: false, error: failure.message)),
      (url) => emit(state.copyWith(uploadingCover: false, coverUrl: url)),
    );
  }

  Future<void> save({required Event original, required Event edited}) async {
    if (state.status == EditEventStatus.saving) return;
    emit(state.copyWith(status: EditEventStatus.saving));
    final result = await updateEvent(
      UpdateEventParams(original: original, edited: edited),
    );
    result.fold(
      (failure) => emit(state.copyWith(
        status: EditEventStatus.editing,
        error: failure.message,
      )),
      (event) => emit(state.copyWith(
        status: EditEventStatus.saved,
        saved: event,
      )),
    );
  }
}
