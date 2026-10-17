import 'package:flutter_test/flutter_test.dart';
import 'package:vito_app/features/auth/data/models/user_model.dart';

import 'fixture_loader.dart';

/// T-C04 — Serialización del contrato de autenticación.
///
/// Fuentes: `docs/api/fixtures/authentication/*.2xx.json` (T-C01),
/// envelope unificado de ADR-004 y `docs/api/CONVENTIONS.md` §3 y §6.
///
/// Si un fixture cambia y deja de cumplir el contrato, estos tests fallan.
void main() {
  const successFixtures = <String>[
    'authentication/registerUser.201.json',
    'authentication/loginUser.200.json',
    'authentication/refreshToken.200.json',
  ];

  group('AuthResponse (ADR-004)', () {
    for (final path in successFixtures) {
      test('$path conserva el envelope de tokens y parsea el usuario', () {
        final json = loadFixtureObject(path);

        expect(
          json.keys,
          containsAll(<String>[
            'accessToken',
            'refreshToken',
            'tokenType',
            'expiresIn',
            'user',
          ]),
          reason: 'ADR-004 exige accessToken/refreshToken/tokenType/'
              'expiresIn/user en toda respuesta de auth',
        );

        expect(json['accessToken'], isA<String>());
        expect(
          (json['accessToken'] as String).isNotEmpty,
          isTrue,
          reason: 'el accessToken no puede venir vacío',
        );
        expect(json['refreshToken'], isA<String>());
        expect(json['tokenType'], 'Bearer');
        expect(json['expiresIn'], isA<int>());
        expect(
          json['expiresIn'] as int,
          greaterThan(0),
          reason: 'expiresIn son segundos restantes; debe ser positivo',
        );

        final user = json['user'];
        expect(user, isA<Map<String, dynamic>>(),
            reason: 'el usuario viaja dentro de AuthResponse.user (ADR-004)');

        final model = UserModel.fromJson(user as Map<String, dynamic>);
        expect(model.id, isNotEmpty);
        expect(model.id, startsWith('usr_'),
            reason: 'CONVENTIONS §6: ids prefijados por tipo');
        expect(model.username, isNotEmpty);
        expect(model.email, contains('@'));
        expect(model.profile, isNotNull,
            reason: 'el fixture ejerce el perfil completo (UserProfile)');
        expect(model.stats, isNotNull,
            reason: 'el fixture ejerce los contadores (UserStats)');
      });
    }

    test('UserModel sobrevive el round-trip fromJson → toJson → fromJson', () {
      final json = loadFixtureObject('authentication/loginUser.200.json');
      final first =
          UserModel.fromJson(json['user'] as Map<String, dynamic>);
      final second = UserModel.fromJson(first.toJson());

      expect(second.id, first.id);
      expect(second.username, first.username);
      expect(second.email, first.email);
      expect(second.profile?.displayName, first.profile?.displayName);
      expect(second.stats?.followersCount, first.stats?.followersCount);
    });

    test('loginUser.200.json mantiene la identidad de ejemplo', () {
      // Expectativa fijada a mano: si alguien edita el fixture, este test
      // falla y obliga a revisar si el cambio es deliberado.
      final json = loadFixtureObject('authentication/loginUser.200.json');
      final user = json['user'] as Map<String, dynamic>;

      expect(user['id'], 'usr_01H8X2K4QJ');
      expect(user['username'], 'alex_villa');
      expect(user['email'], 'alex@vito.com');
    });
  });
}
