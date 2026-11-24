import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/user_summary.dart';
import '../repositories/social_repository.dart';

/// Longitud mínima de una búsqueda, sin contar una `@` inicial (contrato).
const minSearchQueryLength = 2;

/// Quita espacios y una `@` inicial (`@caro.mz` busca `caro.mz`).
String normalizeSearchQuery(String raw) {
  final trimmed = raw.trim();
  return trimmed.startsWith('@') ? trimmed.substring(1).trim() : trimmed;
}

/// `searchUsers` (Ola 2a, T034): busca usuarios registrados para invitarlos
/// o seguirlos.
class SearchUsersUseCase implements UseCase<List<UserSummary>, String> {
  final SocialRepository repository;

  SearchUsersUseCase(this.repository);

  /// [query] tal como lo escribió la persona. Por debajo de
  /// [minSearchQueryLength] caracteres devuelve una lista vacía sin llamar
  /// al backend (que respondería `400`).
  @override
  Future<Either<Failure, List<UserSummary>>> call(String query) async {
    final normalized = normalizeSearchQuery(query);
    if (normalized.length < minSearchQueryLength) return const Right([]);
    return repository.searchUsers(
      query: normalized.length > 50 ? normalized.substring(0, 50) : normalized,
    );
  }
}
