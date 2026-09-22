import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../entities/social_profile.dart';
import '../repositories/social_repository.dart';

class GetProfileUseCase implements UseCase<SocialProfile, String> {
  final SocialRepository repository;
  
  GetProfileUseCase(this.repository);
  
  @override
  Future<Either<Failure, SocialProfile>> call(String userId) async {
    return await repository.getProfile(userId);
  }
}
