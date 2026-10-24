import 'dart:async';

import 'package:dartz/dartz.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vito_app/core/error/failures.dart';
import 'package:vito_app/features/mosaic/data/models/mosaic_model.dart';
import 'package:vito_app/features/mosaic/domain/entities/mosaic.dart';
import 'package:vito_app/features/mosaic/domain/repositories/mosaic_repository.dart';

import '../../contract/fixture_loader.dart';

/// Feed de la Ola 3 (`listMosaicFeed.200.json`).
List<MosaicFeedItem> feedFixture() => loadFixtureList('mosaic/listMosaicFeed.200.json')
    .map((json) => MosaicFeedItemModel.fromJson(json as Map<String, dynamic>))
    .toList();

/// Grilla de la Ola 3 (`getMosaicGrid.200.json`).
MosaicGrid gridFixture() =>
    MosaicGridModel.fromJson(loadFixtureObject('mosaic/getMosaicGrid.200.json'));

/// Ids de las celdas ocupadas de [grid].
List<String> occupiedCellIds(MosaicGrid grid) => grid.grid
    .expand((row) => row)
    .whereType<MosaicItem>()
    .map((item) => item.id)
    .toList();

/// Repositorio del mosaico para T-F10. Solo implementa lo que usan la
/// moderación y la página; el resto lanza (`Fake`).
///
/// Si [feed] o [grid] son `null`, la carga correspondiente no termina nunca,
/// para que el estado final del bloc sea el de la otra carga.
class FakeMosaicRepository extends Fake implements MosaicRepository {
  FakeMosaicRepository({this.feed, this.grid, this.removeFailure});

  final List<MosaicFeedItem>? feed;
  final MosaicGrid? grid;
  Failure? removeFailure;

  final List<String> removed = <String>[];
  final List<String> connectedEvents = <String>[];
  int disconnects = 0;

  final StreamController<String> itemRemovedController =
      StreamController<String>.broadcast();
  final StreamController<MosaicFeedItem> feedController =
      StreamController<MosaicFeedItem>.broadcast();
  final StreamController<MosaicGrid> gridController =
      StreamController<MosaicGrid>.broadcast();
  final StreamController<MosaicLikeCountUpdate> likeController =
      StreamController<MosaicLikeCountUpdate>.broadcast();

  @override
  Future<Either<Failure, List<MosaicFeedItem>>> getEventFeed({
    required String eventId,
    int page = 0,
    int size = 20,
  }) {
    final value = feed;
    if (value == null) {
      return Completer<Either<Failure, List<MosaicFeedItem>>>().future;
    }
    return Future.value(Right<Failure, List<MosaicFeedItem>>(value));
  }

  @override
  Future<Either<Failure, MosaicGrid>> getEventGrid(String eventId) {
    final value = grid;
    if (value == null) {
      return Completer<Either<Failure, MosaicGrid>>().future;
    }
    return Future.value(Right<Failure, MosaicGrid>(value));
  }

  @override
  Future<Either<Failure, void>> removeMosaicItem(String itemId) async {
    removed.add(itemId);
    final failure = removeFailure;
    if (failure != null) return Left(failure);
    return const Right(null);
  }

  @override
  Stream<MosaicFeedItem> get feedStream => feedController.stream;

  @override
  Stream<MosaicGrid> get gridStream => gridController.stream;

  @override
  Stream<MosaicLikeCountUpdate> get likeStream => likeController.stream;

  @override
  Stream<String> get itemRemovedStream => itemRemovedController.stream;

  @override
  void connectToWebSocket(String eventId) => connectedEvents.add(eventId);

  @override
  void disconnectFromWebSocket() => disconnects++;
}
