import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/config/env/env.dart';

void main() {
  group('Env.resolveBaseUrl', () {
    test('usa el valor configurado por --dart-define', () {
      expect(
        Env.resolveBaseUrl(
          'https://api.vito.app/api/v1',
          Env.defaultApiBaseUrl,
          releaseMode: true,
        ),
        'https://api.vito.app/api/v1',
      );
    });

    test('en debug cae al default localhost', () {
      expect(
        Env.resolveBaseUrl(
          '',
          Env.defaultApiBaseUrl,
          releaseMode: false,
        ),
        Env.defaultApiBaseUrl,
      );
    });

    test('en release sin valor falla el arranque', () {
      expect(
        () => Env.resolveBaseUrl(
          '',
          Env.defaultApiBaseUrl,
          releaseMode: true,
        ),
        throwsStateError,
      );
    });

    test('un valor vacío o con espacios cuenta como no definido', () {
      expect(
        () => Env.resolveBaseUrl(
          '   ',
          Env.defaultWsBaseUrl,
          name: 'WS_BASE_URL',
          releaseMode: true,
        ),
        throwsStateError,
      );
    });
  });

  group('Env en tests (debug, sin dart-define)', () {
    test('los defaults localhost solo aplican en debug', () {
      expect(Env.apiBaseUrl, Env.defaultApiBaseUrl);
      expect(Env.wsBaseUrl, Env.defaultWsBaseUrl);
    });

    test('sin variables no está configurado para producción', () {
      expect(Env.isConfiguredForProduction, isFalse);
    });
  });
}
