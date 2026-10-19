import 'package:flutter_test/flutter_test.dart';
import 'package:vito_app/features/auth/data/models/user_model.dart';

import 'fixture_loader.dart';

/// T-C04 / T-C08 — Serialización del contrato de autenticación.
///
/// Fuentes: `docs/api/fixtures/authentication/*.2xx.json` (T-C01),
/// `AuthResponse` / `AuthUser` de ADR-004 y `docs/api/CONVENTIONS.md` §3 y §6.
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
          unorderedEquals(<String>[
            'accessToken',
            'refreshToken',
            'expiresIn',
            'user',
          ]),
          reason: 'ADR-004: accessToken/refreshToken/expiresIn/user, '
              'sin tokenType',
        );

        expect(json['accessToken'], isA<String>());
        expect(
          (json['accessToken'] as String).isNotEmpty,
          isTrue,
          reason: 'el accessToken no puede venir vacío',
        );
        expect(json['refreshToken'], isA<String>());
        expect(json['expiresIn'], isA<int>());
        expect(
          json['expiresIn'] as int,
          greaterThan(0),
          reason: 'expiresIn son segundos restantes; debe ser positivo',
        );

        final user = json['user'];
        expect(user, isA<Map<String, dynamic>>(),
            reason: 'el usuario viaja dentro de AuthResponse.user (ADR-004)');
        expect(
          (user as Map<String, dynamic>).keys,
          unorderedEquals(
              <String>['id', 'username', 'email', 'displayName', 'avatar']),
          reason: 'AuthUser es plano: sin profile ni stats (ADR-004)',
        );

        final model = UserModel.fromAuthJson(user);
        expect(model.id, isNotEmpty);
        expect(model.id, startsWith('usr_'),
            reason: 'CONVENTIONS §6: ids prefijados por tipo');
        expect(model.username, isNotEmpty);
        expect(model.email, contains('@'));
        expect(model.profile?.displayName, user['displayName'],
            reason: 'displayName plano llega a la UI vía profile');
        expect(model.profile?.avatar, user['avatar']);
        expect(model.stats, isNull,
            reason: 'auth no conoce los contadores de social');
      });
    }

    test('UserModel sobrevive el round-trip fromAuthJson → toAuthJson', () {
      final json = loadFixtureObject('authentication/loginUser.200.json');
      final user = json['user'] as Map<String, dynamic>;

      expect(UserModel.fromAuthJson(user).toAuthJson(), user);
    });

    test('AuthUser acepta displayName y avatar nulos', () {
      final model = UserModel.fromAuthJson(const {
        'id': 'usr_01H8X2K4QJ',
        'username': 'alex_villa',
        'email': 'alex@vito.com',
        'displayName': null,
        'avatar': null,
      });

      expect(model.profile, isNull);
      expect(model.username, 'alex_villa');
    });

    test('loginUser.200.json mantiene la identidad de ejemplo', () {
      // Expectativa fijada a mano: si alguien edita el fixture, este test
      // falla y obliga a revisar si el cambio es deliberado.
      final json = loadFixtureObject('authentication/loginUser.200.json');
      final user = json['user'] as Map<String, dynamic>;

      expect(user['id'], 'usr_01H8X2K4QJ');
      expect(user['username'], 'alex_villa');
      expect(user['email'], 'alex@vito.com');
      expect(user['displayName'], 'Alex Villa');
    });
  });
}
