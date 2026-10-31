import 'package:bloc_test/bloc_test.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/features/auth/presentation/bloc/auth_bloc.dart';
import 'package:vito_app/features/auth/presentation/pages/login_page.dart';
import 'package:vito_app/features/auth/presentation/widgets/forgot_password_notice.dart';

class MockAuthBloc extends MockBloc<AuthEvent, AuthState> implements AuthBloc {}

Widget _host(String supportEmail) => MaterialApp(
      home: Scaffold(
        body: Builder(
          builder: (context) => TextButton(
            onPressed: () =>
                showForgotPasswordNotice(context, supportEmail: supportEmail),
            child: const Text('abrir'),
          ),
        ),
      ),
    );

/// Bug-F11: "¿Olvidaste tu contraseña?" explica qué hacer mientras no exista
/// la recuperación (RFC AppViTo/ViTo#213).
void main() {
  testWidgets('el botón del login abre el aviso', (tester) async {
    tester.view.physicalSize = const Size(1200, 2400);
    tester.view.devicePixelRatio = 2.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final authBloc = MockAuthBloc();
    whenListen(authBloc, const Stream<AuthState>.empty(),
        initialState: const AuthInitial());

    await tester.pumpWidget(MaterialApp(
      home: BlocProvider<AuthBloc>.value(
        value: authBloc,
        child: const LoginPage(),
      ),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.text('¿Olvidaste tu contraseña?'));
    await tester.pumpAndSettle();

    expect(find.byType(ForgotPasswordNotice), findsOneWidget);
    expect(find.text('Recuperar contraseña'), findsOneWidget);
  });

  testWidgets('con correo de soporte lo muestra y se puede copiar',
      (tester) async {
    String? copied;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          copied = (call.arguments as Map)['text'] as String?;
        }
        return null;
      },
    );
    addTearDown(() => tester.binding.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, null));

    await tester.pumpWidget(_host('soporte@example.test'));
    await tester.tap(find.text('abrir'));
    await tester.pumpAndSettle();

    expect(find.text('soporte@example.test'), findsOneWidget);
    expect(find.text('Copiar correo'), findsOneWidget);

    await tester.tap(find.text('Copiar correo'));
    await tester.pump();
    expect(find.text('Correo copiado'), findsOneWidget);
    expect(copied, 'soporte@example.test');
  });

  testWidgets('sin correo de soporte no muestra correo ni botón de copiar',
      (tester) async {
    await tester.pumpWidget(_host(''));
    await tester.tap(find.text('abrir'));
    await tester.pumpAndSettle();

    expect(find.textContaining('persona que te invitó'), findsOneWidget);
    expect(find.text('Copiar correo'), findsNothing);

    await tester.tap(find.text('Entendido'));
    await tester.pumpAndSettle();
    expect(find.byType(ForgotPasswordNotice), findsNothing);
  });
}
