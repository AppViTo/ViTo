import 'package:dartz/dartz.dart';

import '../../../../core/error/failures.dart';
import '../../../../core/usecase/usecase.dart';
import '../repositories/checkin_repository.dart';

class ValidateCheckInUseCase implements UseCase<CheckInResult, ValidateCheckInParams> {
  final CheckInRepository repository;
  
  ValidateCheckInUseCase(this.repository);
  
  @override
  Future<Either<Failure, CheckInResult>> call(ValidateCheckInParams params) async {
    return await repository.validateAndCheckIn(
      qrCode: params.qrCode,
      eventId: params.eventId,
      location: params.location,
      latitude: params.latitude,
      longitude: params.longitude,
    );
  }
}

class ValidateCheckInParams {
  final String qrCode;
  final String eventId;
  final String? location;
  final double? latitude;
  final double? longitude;
  
  ValidateCheckInParams({
    required this.qrCode,
    required this.eventId,
    this.location,
    this.latitude,
    this.longitude,
  });
}
