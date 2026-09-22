import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../entities/social_profile.dart';

abstract class SocialRepository {
  Future<Either<Failure, void>> follow(String userId);
  
  Future<Either<Failure, void>> unfollow(String userId);
  
  Future<Either<Failure, bool>> isFollowing(String userId);
  
  Future<Either<Failure, SocialProfile>> getProfile(String userId);
  
  Future<Either<Failure, List<SocialProfile>>> getFollowers({
    required String userId,
    int page = 0,
    int size = 20,
  });
  
  Future<Either<Failure, List<SocialProfile>>> getFollowing({
    required String userId,
    int page = 0,
    int size = 20,
  });
  
  Future<Either<Failure, List<SocialProfile>>> searchUsers({
    required String query,
    int page = 0,
    int size = 20,
  });
}
