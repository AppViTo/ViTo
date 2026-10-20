import 'package:get_it/get_it.dart';
import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../../config/env/env.dart';
import '../../core/network/token_interceptor.dart';
import '../../features/auth/data/datasources/auth_remote_datasource.dart';
import '../../features/auth/data/datasources/auth_local_datasource.dart';
import '../../features/auth/data/repositories/auth_repository_impl.dart';
import '../../features/auth/domain/repositories/auth_repository.dart';
import '../../features/auth/domain/usecases/login.dart';
import '../../features/auth/domain/usecases/register.dart';
import '../../features/auth/domain/usecases/logout.dart';
import '../../features/auth/domain/usecases/restore_session.dart';
import '../../features/auth/presentation/bloc/auth_bloc.dart';

import '../../features/events/data/datasources/event_remote_datasource.dart';
import '../../features/events/data/datasources/media_remote_datasource.dart';
import '../../features/events/data/repositories/event_repository_impl.dart';
import '../../features/events/domain/repositories/event_repository.dart';
import '../../features/events/domain/usecases/create_event.dart';
import '../../features/events/domain/usecases/respond_rsvp.dart';
import '../../features/events/presentation/bloc/event_bloc.dart';

import '../../features/streaming/data/datasources/streaming_remote_datasource.dart';
import '../../features/streaming/data/repositories/streaming_repository_impl.dart';
import '../../features/streaming/domain/repositories/streaming_repository.dart';
import '../../features/streaming/presentation/bloc/streaming_bloc.dart';

import '../../features/social/data/datasources/social_remote_datasource.dart';
import '../../features/social/data/repositories/social_repository_impl.dart';
import '../../features/social/domain/repositories/social_repository.dart';
import '../../features/social/domain/usecases/follow.dart';
import '../../features/social/domain/usecases/unfollow.dart';
import '../../features/social/domain/usecases/get_profile.dart';
import '../../features/social/presentation/bloc/social_bloc.dart';

import '../../features/checkin/data/datasources/checkin_remote_datasource.dart';
import '../../features/checkin/data/repositories/checkin_repository_impl.dart';
import '../../features/checkin/domain/repositories/checkin_repository.dart';
import '../../features/checkin/domain/usecases/generate_ticket.dart';
import '../../features/checkin/domain/usecases/validate_checkin.dart';
import '../../features/checkin/presentation/bloc/checkin_bloc.dart';

import '../../features/mosaic/data/datasources/mosaic_remote_datasource.dart';
import '../../features/mosaic/data/repositories/mosaic_repository_impl.dart';
import '../../features/mosaic/domain/repositories/mosaic_repository.dart';
import '../../features/mosaic/presentation/bloc/mosaic_bloc.dart';

import '../../features/archive/data/datasources/archive_remote_datasource.dart';
import '../../features/archive/data/repositories/archive_repository_impl.dart';
import '../../features/archive/domain/repositories/archive_repository.dart';
import '../../features/archive/presentation/bloc/archive_bloc.dart';

import '../../features/chat/data/datasources/chat_remote_datasource.dart';
import '../../features/chat/data/repositories/chat_repository_impl.dart';
import '../../features/chat/domain/repositories/chat_repository.dart';
import '../../features/chat/presentation/bloc/chat_bloc.dart';

final sl = GetIt.instance;

Future<void> init() async {
  // External
  final sharedPreferences = await SharedPreferences.getInstance();
  sl.registerLazySingleton(() => sharedPreferences);
  sl.registerLazySingleton(() => const FlutterSecureStorage());
  
  // Dio with interceptors
  final dio = Dio(BaseOptions(
    baseUrl: Env.apiBaseUrl,
    connectTimeout: const Duration(seconds: 5),
    receiveTimeout: const Duration(seconds: 5),
  ));
  
  // Register AuthLocalDataSource first (needed for TokenInterceptor)
  sl.registerLazySingleton<AuthLocalDataSource>(
    () => AuthLocalDataSourceImpl(
      sharedPreferences: sl(),
      secureStorage: sl(),
    ),
  );
  
  // Add token interceptor
  dio.interceptors.add(TokenInterceptor(
    localDataSource: sl(),
    dio: dio,
  ));
  
  sl.registerLazySingleton(() => dio);
  
  // Features - Auth
  sl.registerLazySingleton<AuthRemoteDataSource>(
    () => AuthRemoteDataSourceImpl(dio: sl()),
  );
  sl.registerLazySingleton<AuthRepository>(
    () => AuthRepositoryImpl(
      remoteDataSource: sl(),
      localDataSource: sl(),
    ),
  );
  sl.registerLazySingleton(() => LoginUseCase(sl()));
  sl.registerLazySingleton(() => RegisterUseCase(sl()));
  sl.registerLazySingleton(() => LogoutUseCase(sl()));
  sl.registerLazySingleton(() => RestoreSessionUseCase(sl()));
  sl.registerFactory(
    () => AuthBloc(
      loginUseCase: sl(),
      registerUseCase: sl(),
      logoutUseCase: sl(),
      restoreSessionUseCase: sl(),
    ),
  );
  
  // Features - Events
  sl.registerLazySingleton<EventRemoteDataSource>(
    () => EventRemoteDataSourceImpl(dio: sl()),
  );
  sl.registerLazySingleton<MediaRemoteDataSource>(
    () => MediaRemoteDataSourceImpl(dio: sl()),
  );
  sl.registerLazySingleton<EventRepository>(
    () => EventRepositoryImpl(
      remoteDataSource: sl(),
      mediaDataSource: sl(),
    ),
  );
  sl.registerLazySingleton(() => CreateEventUseCase(sl()));
  sl.registerLazySingleton(() => RespondRSVPUseCase(sl()));
  sl.registerFactory(
    () => EventBloc(
      eventRepository: sl(),
    ),
  );
  
  // Features - Streaming
  sl.registerLazySingleton<StreamingRemoteDataSource>(
    () => StreamingRemoteDataSourceImpl(dio: sl()),
  );
  sl.registerLazySingleton<StreamingRepository>(
    () => StreamingRepositoryImpl(remoteDataSource: sl()),
  );
  sl.registerFactory(
    () => StreamingBloc(
      repository: sl(),
    ),
  );
  
  // Features - Social
  sl.registerLazySingleton<SocialRemoteDataSource>(
    () => SocialRemoteDataSourceImpl(dio: sl()),
  );
  sl.registerLazySingleton<SocialRepository>(
    () => SocialRepositoryImpl(remoteDataSource: sl()),
  );
  sl.registerLazySingleton(() => FollowUseCase(sl()));
  sl.registerLazySingleton(() => UnfollowUseCase(sl()));
  sl.registerLazySingleton(() => GetProfileUseCase(sl()));
  sl.registerFactory(
    () => SocialBloc(
      followUseCase: sl(),
      unfollowUseCase: sl(),
      getProfileUseCase: sl(),
      socialRepository: sl(),
    ),
  );
  
  // Features - Check-In
  sl.registerLazySingleton<CheckInRemoteDataSource>(
    () => CheckInRemoteDataSourceImpl(dio: sl()),
  );
  sl.registerLazySingleton<CheckInRepository>(
    () => CheckInRepositoryImpl(remoteDataSource: sl()),
  );
  sl.registerLazySingleton(() => GenerateTicketUseCase(sl()));
  sl.registerLazySingleton(() => ValidateCheckInUseCase(sl()));
  sl.registerFactory(
    () => CheckInBloc(
      repository: sl(),
    ),
  );
  
  // Features - Mosaic
  sl.registerLazySingleton<MosaicRemoteDataSource>(
    () => MosaicRemoteDataSourceImpl(dio: sl()),
  );
  sl.registerLazySingleton<MosaicRepository>(
    () => MosaicRepositoryImpl(remoteDataSource: sl()),
  );
  sl.registerFactory(
    () => MosaicBloc(
      repository: sl(),
    ),
  );
  
  // Features - Archive
  sl.registerLazySingleton<ArchiveRemoteDataSource>(
    () => ArchiveRemoteDataSourceImpl(dio: sl()),
  );
  sl.registerLazySingleton<ArchiveRepository>(
    () => ArchiveRepositoryImpl(remoteDataSource: sl()),
  );
  sl.registerFactory(
    () => ArchiveBloc(
      repository: sl(),
    ),
  );
  
  // Features - Chat
  sl.registerLazySingleton<ChatRemoteDataSource>(
    () => ChatRemoteDataSource(dio: sl()),
  );
  sl.registerLazySingleton<ChatRepository>(
    () => ChatRepositoryImpl(remoteDataSource: sl()),
  );
  sl.registerFactory(
    () => ChatBloc(
      repository: sl(),
    ),
  );
}
