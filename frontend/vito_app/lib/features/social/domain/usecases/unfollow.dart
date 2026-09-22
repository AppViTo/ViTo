import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../repositories/social_repository.dart';

class UnfollowUseCase implements UseCase<void, String> {
  final SocialRepository repository;
  
  UnfollowUseCase(this.repository);
  
  @override
  Future<Either<Failure, void>> call(String userId) async {
    return await repository.unfollow(userId);
  }
}
