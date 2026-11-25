import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vito_app/core/theme/app_colors.dart';
import 'package:vito_app/core/theme/app_theme.dart';

/// El título del AppBar debe heredar el color del tema. Un `titleTextStyle`
/// sin color lo dejaba negro sobre el fondo oscuro (Seguidores, Siguiendo,
/// Editar evento...).
void main() {
  Future<Color?> titleColor(WidgetTester tester, ThemeData theme) async {
    await tester.pumpWidget(MaterialApp(
      theme: theme,
      home: Scaffold(appBar: AppBar(title: const Text('Seguidores'))),
    ));
    final paragraph = tester.renderObject<RenderParagraph>(find.text('Seguidores'));
    return paragraph.text.style?.color;
  }

  testWidgets('tema oscuro: el título del AppBar sale en blancoHielo', (tester) async {
    expect(await titleColor(tester, AppTheme.darkTheme), AppColors.blancoHielo);
  });

  testWidgets('tema claro: el título del AppBar sale en grisCarbon', (tester) async {
    expect(await titleColor(tester, AppTheme.lightTheme), AppColors.grisCarbon);
  });
}
