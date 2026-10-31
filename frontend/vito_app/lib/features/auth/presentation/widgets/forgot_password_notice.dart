import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../../config/env/env.dart';
import '../../../../core/theme/app_colors.dart';

/// Bug-F11: la recuperación de contraseña aún no existe (RFC AppViTo/ViTo#213).
/// Mientras tanto, el botón del login explica qué hacer en lugar de no responder.
Future<void> showForgotPasswordNotice(
  BuildContext context, {
  String supportEmail = Env.supportEmail,
}) {
  return showDialog<void>(
    context: context,
    builder: (_) => ForgotPasswordNotice(supportEmail: supportEmail),
  );
}

class ForgotPasswordNotice extends StatelessWidget {
  const ForgotPasswordNotice({super.key, this.supportEmail = ''});

  /// Correo de soporte del equipo; llega por `--dart-define=SUPPORT_EMAIL`, nunca del repo.
  final String supportEmail;

  @override
  Widget build(BuildContext context) {
    final email = supportEmail.trim();

    return AlertDialog(
      backgroundColor: AppColors.surfaceDark,
      title: const Text(
        'Recuperar contraseña',
        style: TextStyle(color: AppColors.blancoHielo),
      ),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Todavía no puedes restablecer tu contraseña desde la app. '
            'Ya estamos trabajando en ello.',
            style: TextStyle(color: AppColors.textSecondary),
          ),
          const SizedBox(height: 12),
          if (email.isNotEmpty) ...[
            const Text(
              'Mientras tanto, escríbenos desde el correo con el que te '
              'registraste y te ayudamos a recuperar tu cuenta:',
              style: TextStyle(color: AppColors.textSecondary),
            ),
            const SizedBox(height: 8),
            SelectableText(
              email,
              style: const TextStyle(
                color: AppColors.lavandaPale,
                fontWeight: FontWeight.w600,
              ),
            ),
          ] else
            const Text(
              'Mientras tanto, pídele ayuda a la persona que te invitó a ViTo '
              'para que nos contacte.',
              style: TextStyle(color: AppColors.textSecondary),
            ),
        ],
      ),
      actions: [
        if (email.isNotEmpty)
          TextButton(
            onPressed: () {
              Clipboard.setData(ClipboardData(text: email));
              ScaffoldMessenger.maybeOf(context)?.showSnackBar(
                const SnackBar(content: Text('Correo copiado')),
              );
            },
            child: const Text('Copiar correo'),
          ),
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Entendido'),
        ),
      ],
    );
  }
}
