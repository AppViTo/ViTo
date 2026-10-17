import 'dart:convert';
import 'dart:io';

/// Carga fixtures desde `docs/api/fixtures/` (T-C01) para los tests de
/// serialización de T-C04.
///
/// La raíz del monorepo se localiza subiendo desde [Directory.current] hasta
/// encontrar `docs/api/fixtures`, de modo que el test funciona tanto si
/// `flutter test` se lanza desde `frontend/vito_app` como desde la raíz.
Directory findRepoRoot() {
  var dir = Directory.current.absolute;
  for (var i = 0; i < 8; i++) {
    if (Directory('${dir.path}/docs/api/fixtures').existsSync()) return dir;
    final parent = dir.parent;
    if (parent.path == dir.path) break;
    dir = parent;
  }
  throw StateError(
    'No se encontró docs/api/fixtures subiendo desde ${Directory.current.path}. '
    '¿Existe la rama con T-C01 mergeada y se lanza desde el monorepo?',
  );
}

String fixturePath(String relative) =>
    '${findRepoRoot().path}/docs/api/fixtures/$relative';

/// Lee un fixture que debe ser un objeto JSON.
Map<String, dynamic> loadFixtureObject(String relative) {
  final decoded = jsonDecode(File(fixturePath(relative)).readAsStringSync());
  if (decoded is! Map<String, dynamic>) {
    throw StateError('$relative: se esperaba un objeto JSON y llegó '
        '${decoded.runtimeType}');
  }
  return decoded;
}

/// Lee un fixture que debe ser un array JSON plano (ADR-002).
List<dynamic> loadFixtureList(String relative) {
  final decoded = jsonDecode(File(fixturePath(relative)).readAsStringSync());
  if (decoded is! List) {
    throw StateError(
      '$relative: ADR-002 exige un array plano y llegó ${decoded.runtimeType}',
    );
  }
  return decoded;
}
