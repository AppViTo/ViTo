import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../repositories/social_repository.dart';

class FollowUseCase implements UseCase<void, String> {
  final SocialRepository repository;
  
  FollowUseCase(this.repository);
  
  @override
  Future<Either<Failure, void>> call(String userId) async {
    return await repository.follow(userId);
  }
}
