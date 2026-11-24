import 'package:dio/dio.dart';

import '../../../../core/error/dio_error_handler.dart';
import '../../../../core/error/exceptions.dart';
import '../models/user_summary_model.dart';

/// Mensaje para el `429` de `searchUsers` (1 búsqueda por segundo por usuario
/// con ráfaga de 10, CONVENTIONS §9).
const searchRateLimitedMessage = 'Espera un momento y vuelve a intentarlo';

/// Usuarios de `auth-service` (Ola 2a, T034): dueño de la identidad
/// (ADR-004, ADR-016).
abstract class UserRemoteDataSource {
  /// `searchUsers` (`GET /users/search`): por prefijo de usuario o nombre,
  /// sin tildes. Nunca busca por correo; no incluye a quien busca ni a los
  /// perfiles privados. `query` de 2 a 50 caracteres tras quitar una `@`.
  Future<List<UserSummaryModel>> searchUsers({
    required String query,
    int page = 0,
    int size = 20,
  });
}

class UserRemoteDataSourceImpl implements UserRemoteDataSource {
  final Dio dio;

  UserRemoteDataSourceImpl({required this.dio});

  @override
  Future<List<UserSummaryModel>> searchUsers({
    required String query,
    int page = 0,
    int size = 20,
  }) async {
    try {
      final response = await dio.get(
        '/users/search',
        queryParameters: {'query': query, 'page': page, 'size': size},
      );
      return (response.data as List)
          .map((json) => UserSummaryModel.fromJson(json as Map<String, dynamic>))
          .toList();
    } on DioException catch (e) {
      if (e.response?.statusCode == 429) {
        throw const ServerException(
          message: searchRateLimitedMessage,
          statusCode: 429,
        );
      }
      throw handleDioError(e);
    }
  }
}
