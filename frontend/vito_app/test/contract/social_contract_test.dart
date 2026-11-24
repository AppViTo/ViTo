import 'package:flutter_test/flutter_test.dart';
import 'package:vito_app/features/social/data/models/social_profile_model.dart';
import 'package:vito_app/features/social/data/models/user_summary_model.dart';

import 'fixture_loader.dart';

/// T012 (spec 004, Ola 2a) — `SocialProfile` y las listas del grafo contra
/// `docs/api/fixtures/social/`.
void main() {
  group('SocialProfile', () {
    test('getSocialProfile.other.200.json: isFollowing e isFollowedBy', () {
      final json = loadFixtureObject('social/getSocialProfile.other.200.json');
      final profile = SocialProfileModel.fromJson(json);

      expect(profile.userId, 'usr_01H8X2K4QV');
      expect(profile.username, 'caro.mz');
      expect(profile.displayName, 'Carolina Martínez');
      expect(profile.followersCount, 2);
      expect(profile.followingCount, 1);
      expect(profile.isFollowing, isTrue);
      expect(profile.isFollowedBy, isTrue);
      expect(profile.toJson().keys.toSet(), json.keys.toSet());
    });

    test('getSocialProfile.own.200.json: en el propio perfil son false', () {
      final profile = SocialProfileModel.fromJson(
          loadFixtureObject('social/getSocialProfile.own.200.json'));

      expect(profile.userId, 'usr_01H8X2K4QJ');
      expect(profile.isFollowing, isFalse);
      expect(profile.isFollowedBy, isFalse);
    });

    test('no conserva campos que el contrato no tiene (bio)', () {
      final profile = SocialProfileModel.fromJson({
        ...loadFixtureObject('social/getSocialProfile.other.200.json'),
        'bio': 'no debería leerse',
      });
      expect(profile.toJson().containsKey('bio'), isFalse);
    });

    test('sin displayName el nombre visible es el usuario', () {
      final profile = SocialProfileModel.fromJson({
        ...loadFixtureObject('social/getSocialProfile.other.200.json'),
        'displayName': null,
      });
      expect(profile.displayName, isNull);
      expect(profile.label, 'caro.mz');
    });
  });

  group('listas del grafo', () {
    test('listFollowers.page.200.json y listFollowing.200.json son UserSummary',
        () {
      for (final path in const [
        'social/listFollowers.page.200.json',
        'social/listFollowing.200.json',
      ]) {
        final users = loadFixtureList(path)
            .map((e) => UserSummaryModel.fromJson(e as Map<String, dynamic>))
            .toList();
        expect(users, isNotEmpty, reason: path);
        for (final user in users) {
          expect(user.userId, startsWith('usr_'), reason: path);
        }
      }
    });

    test('listFollowers.empty.200.json es una lista vacía', () {
      expect(loadFixtureList('social/listFollowers.empty.200.json'), isEmpty);
    });
  });
}
