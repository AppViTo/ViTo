import '../../domain/entities/user_summary.dart';

/// `UserSummary` del contrato (Ola 2a, T011): `displayName` y `avatar`
/// pueden ser `null`.
class UserSummaryModel extends UserSummary {
  const UserSummaryModel({
    required super.userId,
    required super.username,
    super.displayName,
    super.avatar,
  });

  factory UserSummaryModel.fromJson(Map<String, dynamic> json) {
    return UserSummaryModel(
      userId: json['userId'] as String,
      username: json['username'] as String,
      displayName: json['displayName'] as String?,
      avatar: json['avatar'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
        'userId': userId,
        'username': username,
        'displayName': displayName,
        'avatar': avatar,
      };
}
