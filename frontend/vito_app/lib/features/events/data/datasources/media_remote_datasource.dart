import 'dart:typed_data';

import 'package:dio/dio.dart';

import '../../../../core/error/dio_error_handler.dart';

abstract class MediaRemoteDataSource {
  Future<String> uploadEventCover({
    required String eventId,
    required Uint8List bytes,
    required String fileName,
    required String mimeType,
  });

  /// `uploadMedia` (`POST /media/upload`) y devuelve el `Media.id`, que es el
  /// `mediaId` de `createMosaicPost` (ADR-016). Cadena vacía si no llega.
  Future<String> uploadMedia({
    required String eventId,
    required Uint8List bytes,
    required String fileName,
    required String mimeType,
  });
}

class MediaRemoteDataSourceImpl implements MediaRemoteDataSource {
  final Dio dio;

  MediaRemoteDataSourceImpl({required this.dio});

  @override
  Future<String> uploadEventCover({
    required String eventId,
    required Uint8List bytes,
    required String fileName,
    required String mimeType,
  }) async {
    final data = await _upload(
      eventId: eventId,
      bytes: bytes,
      fileName: fileName,
      mimeType: mimeType,
    );
    if (data == null) return '';

    final location = data['location'];
    if (location is! Map) return '';

    final url = location['url'];
    return url is String ? url : '';
  }

  @override
  Future<String> uploadMedia({
    required String eventId,
    required Uint8List bytes,
    required String fileName,
    required String mimeType,
  }) async {
    final data = await _upload(
      eventId: eventId,
      bytes: bytes,
      fileName: fileName,
      mimeType: mimeType,
    );
    final id = data?['id'];
    return id is String ? id : '';
  }

  Future<Map<dynamic, dynamic>?> _upload({
    required String eventId,
    required Uint8List bytes,
    required String fileName,
    required String mimeType,
  }) async {
    try {
      final form = FormData.fromMap({
        'eventId': eventId,
        'file': MultipartFile.fromBytes(
          bytes,
          filename: fileName,
          contentType: DioMediaType.parse(mimeType),
        ),
      });

      final response = await dio.post('/media/upload', data: form);
      final data = response.data;
      return data is Map ? data : null;
    } on DioException catch (e) {
      throw handleDioError(e);
    }
  }
}
