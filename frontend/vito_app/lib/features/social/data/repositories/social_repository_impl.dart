import 'package:dartz/dartz.dart';

import '../../../../core/error/exceptions.dart';
import '../../../../core/error/failures.dart';
import '../datasources/social_remote_datasource.dart';
import '../../domain/entities/social_profile.dart';
import '../../domain/repositories/social_repository.dart';

class SocialRepositoryImpl implements SocialRepository {
  final SocialRemoteDataSource remoteDataSource;
  
  SocialRepositoryImpl({required this.remoteDataSource});
  
  @override
  Future<Either<Failure, void>> follow(String userId) async {
    try {
      await remoteDataSource.follow(userId);
      return const Right(null);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, void>> unfollow(String userId) async {
    try {
      await remoteDataSource.unfollow(userId);
      return const Right(null);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, bool>> isFollowing(String userId) async {
    try {
      final result = await remoteDataSource.isFollowing(userId);
      return Right(result);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, SocialProfile>> getProfile(String userId) async {
    try {
      final profile = await remoteDataSource.getProfile(userId);
      return Right(profile);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, List<SocialProfile>>> getFollowers({
    required String userId,
    int page = 0,
    int size = 20,
  }) async {
    try {
      final followers = await remoteDataSource.getFollowers(
        userId: userId,
        page: page,
        size: size,
      );
      return Right(followers);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, List<SocialProfile>>> getFollowing({
    required String userId,
    int page = 0,
    int size = 20,
  }) async {
    try {
      final following = await remoteDataSource.getFollowing(
        userId: userId,
        page: page,
        size: size,
      );
      return Right(following);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
  
  @override
  Future<Either<Failure, List<SocialProfile>>> searchUsers({
    required String query,
    int page = 0,
    int size = 20,
  }) async {
    try {
      final users = await remoteDataSource.searchUsers(
        query: query,
        page: page,
        size: size,
      );
      return Right(users);
    } on ServerException catch (e) {
      return Left(ServerFailure(message: e.message));
    } catch (e) {
      return Left(ServerFailure(message: e.toString()));
    }
  }
}
