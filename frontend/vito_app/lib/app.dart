import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'core/theme/app_theme.dart';
import 'config/routes/app_router.dart';
import 'core/di/injection_container.dart';
import 'features/auth/presentation/bloc/auth_bloc.dart';
import 'features/events/presentation/bloc/event_bloc.dart';
import 'features/chat/presentation/bloc/chat_bloc.dart';
import 'features/checkin/presentation/bloc/checkin_bloc.dart';
import 'features/mosaic/presentation/bloc/mosaic_bloc.dart';
import 'features/social/presentation/bloc/social_bloc.dart';

class ViToApp extends StatelessWidget {
  const ViToApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiBlocProvider(
      providers: [
        BlocProvider<AuthBloc>(
          create: (_) => sl<AuthBloc>()..add(const AuthCheckRequested()),
        ),
        BlocProvider<EventBloc>(
          create: (_) => sl<EventBloc>(),
        ),
        BlocProvider<ChatBloc>(
          create: (_) => sl<ChatBloc>(),
        ),
        BlocProvider<MosaicBloc>(
          create: (_) => sl<MosaicBloc>(),
        ),
        BlocProvider<CheckInBloc>(
          create: (_) => sl<CheckInBloc>(),
        ),
        BlocProvider<SocialBloc>(
          create: (_) => sl<SocialBloc>(),
        ),
      ],
      child: MaterialApp(
        title: 'ViTo',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.lightTheme,
        darkTheme: AppTheme.darkTheme,
        themeMode: ThemeMode.dark,
        localizationsDelegates: const [
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: const [Locale('es'), Locale('en')],
        locale: const Locale('es'),
        initialRoute: AppRouter.initialRoute,
        onGenerateRoute: AppRouter.generateRoute,
      ),
    );
  }
}
