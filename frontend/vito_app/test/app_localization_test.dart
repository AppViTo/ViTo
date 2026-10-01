import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:intl/intl.dart';

void main() {
  setUpAll(() async {
    await initializeDateFormatting('es');
  });

  group('ViTo localization (Fase 1)', () {
    test('DateFormat en es renderiza días y meses en español', () {
      final date = DateTime(2026, 8, 22, 20, 0);
      final formatted = DateFormat("EEEE, d 'de' MMMM", 'es').format(date);

      expect(formatted, 'sábado, 22 de agosto');
      expect(formatted.contains('Saturday'), isFalse);
      expect(formatted.contains('August'), isFalse);
    });

    test('DateFormat de hora en es usa el formato 12h en español', () {
      final date = DateTime(2026, 8, 22, 20, 0);
      // intl >= 0.20 usa U+202F (espacio fino no separable) como separador
      // del meridiano, por eso se normaliza antes de comparar.
      final formatted = DateFormat('h:mm a', 'es')
          .format(date)
          .replaceAll('\u202F', ' ')
          .replaceAll('\u00A0', ' ');

      expect(formatted, '8:00 p. m.');
      expect(DateFormat('h:mm a', 'es').format(DateTime(2026, 8, 22, 2))
              .replaceAll('\u202F', ' ')
              .replaceAll('\u00A0', ' '),
          '2:00 a. m.');
    });

    testWidgets('MaterialApp con los delegates de ViTo resuelve localización es',
        (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          localizationsDelegates: [
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: [Locale('es'), Locale('en')],
          locale: Locale('es'),
          home: Scaffold(body: Text('ok')),
        ),
      );

      expect(tester.widget<MaterialApp>(find.byType(MaterialApp)).locale,
          const Locale('es'));

      final materialLocalizations =
          Localizations.of<MaterialLocalizations>(tester.element(find.byType(Scaffold)), MaterialLocalizations);
      expect(materialLocalizations, isNotNull);
      expect(materialLocalizations!.datePickerHelpText, 'Seleccionar fecha');
    });
  });
}
