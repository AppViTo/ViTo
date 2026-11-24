import 'package:flutter/material.dart';
import 'package:flutter_web_plugins/url_strategy.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'app.dart';
import 'core/di/injection_container.dart' as di;

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // En web, los enlaces de invitación llegan como `/i/<token>`: sin esto
  // Flutter solo lee la ruta después de `#` y abriría el inicio (T043).
  // En móvil no hace nada.
  usePathUrlStrategy();

  await initializeDateFormatting('es');
  await di.init();

  runApp(const ViToApp());
}
