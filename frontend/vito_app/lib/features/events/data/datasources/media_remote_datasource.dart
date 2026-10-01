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
      if (data is! Map) return '';

      final location = data['location'];
      if (location is! Map) return '';

      final url = location['url'];
      return url is String ? url : '';
    } on DioException catch (e) {
      throw handleDioError(e);
    }
  }
}
