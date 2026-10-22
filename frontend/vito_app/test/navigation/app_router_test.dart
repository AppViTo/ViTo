import 'package:dartz/dartz.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/config/routes/app_router.dart';
import 'package:vito_app/core/di/injection_container.dart';
import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/features/archive/domain/entities/archive.dart';
import 'package:vito_app/features/archive/domain/repositories/archive_repository.dart';
import 'package:vito_app/features/archive/presentation/bloc/archive_bloc.dart';
import 'package:vito_app/features/archive/presentation/pages/archive_detail_page.dart';
import 'package:vito_app/features/checkin/domain/repositories/checkin_repository.dart';
import 'package:vito_app/features/checkin/presentation/bloc/checkin_bloc.dart';
import 'package:vito_app/features/events/presentation/pages/qr_scanner_page.dart';

class FakeCheckInRepository implements CheckInRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => null;
}

class FakeArchiveRepository implements ArchiveRepository {
  final EventArchive archive;

  FakeArchiveRepository(this.archive);

  @override
  Future<Either<Failure, EventArchive>> getArchive(String archiveId) async =>
      Right(archive);

  @override
  Future<Either<Failure, List<ArchiveMedia>>> getMedia({
    required String archiveId,
    int page = 0,
    int size = 50,
  }) async =>
      const Right<Failure, List<ArchiveMedia>>([]);

  @override
  Future<Either<Failure, List<ArchiveComment>>> getComments({
    required String archiveId,
    String? mediaId,
    int page = 0,
    int size = 50,
  }) async =>
      const Right<Failure, List<ArchiveComment>>([]);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Widget _navigationHarness({
  required List<BlocProvider> providers,
  required String route,
  required Map<String, dynamic> arguments,
}) {
  final app = MaterialApp(
    onGenerateRoute: AppRouter.generateRoute,
    home: Builder(
      builder: (context) => Scaffold(
        body: Center(
          child: ElevatedButton(
            onPressed: () =>
                Navigator.of(context).pushNamed(route, arguments: arguments),
            child: const Text('abrir'),
          ),
        ),
      ),
    ),
  );

  if (providers.isEmpty) {
    return app;
  }
  return MultiBlocProvider(providers: providers, child: app);
}

void main() {
  tearDown(() async {
    await sl.reset();
  });

  testWidgets('la ruta de check-in resuelve con CheckInBloc provisto',
      (tester) async {
    await tester.pumpWidget(_navigationHarness(
      providers: [
        BlocProvider<CheckInBloc>(
          create: (_) => CheckInBloc(repository: FakeCheckInRepository()),
        ),
      ],
      route: AppRouter.qrScanner,
      arguments: {'eventId': 'evt_1'},
    ));

    await tester.tap(find.text('abrir'));
    await tester.pump();
    await tester.pump();

    expect(find.byType(QrScannerPage), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('la ruta de detalle de archivo resuelve con su archiveId',
      (tester) async {
    const archive = EventArchive(
      id: 'arc_1',
      eventId: 'evt_1',
      organizerId: 'usr_1',
      title: 'Boda de Ana',
      status: ArchiveStatus.completed,
    );
    sl.registerFactory<ArchiveBloc>(
      () => ArchiveBloc(repository: FakeArchiveRepository(archive)),
    );

    await tester.pumpWidget(_navigationHarness(
      providers: const <BlocProvider>[],
      route: AppRouter.archiveDetail,
      arguments: {'archiveId': 'arc_1'},
    ));

    await tester.tap(find.text('abrir'));
    await tester.pumpAndSettle();

    expect(find.byType(ArchiveDetailPage), findsOneWidget);
    expect(find.text('Boda de Ana'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
