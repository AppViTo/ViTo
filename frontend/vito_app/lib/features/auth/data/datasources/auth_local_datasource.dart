import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

abstract class AuthLocalDataSource {
  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
  });
  
  Future<String?> getAccessToken();
  
  Future<String?> getRefreshToken();
  
  Future<void> clearTokens();
  
  Future<bool> isLoggedIn();
  
  Future<void> saveUserId(String userId);
  
  Future<String?> getUserId();
}

class AuthLocalDataSourceImpl implements AuthLocalDataSource {
  final FlutterSecureStorage secureStorage;
  final SharedPreferences sharedPreferences;
  
  static const String _accessTokenKey = 'access_token';
  static const String _refreshTokenKey = 'refresh_token';
  static const String _userIdKey = 'user_id';
  static const String _isLoggedInKey = 'is_logged_in';
  
  AuthLocalDataSourceImpl({
    required this.secureStorage,
    required this.sharedPreferences,
  });
  
  @override
  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    await secureStorage.write(key: _accessTokenKey, value: accessToken);
    await secureStorage.write(key: _refreshTokenKey, value: refreshToken);
    await sharedPreferences.setBool(_isLoggedInKey, true);
  }
  
  @override
  Future<String?> getAccessToken() async {
    return await secureStorage.read(key: _accessTokenKey);
  }
  
  @override
  Future<String?> getRefreshToken() async {
    return await secureStorage.read(key: _refreshTokenKey);
  }
  
  @override
  Future<void> clearTokens() async {
    await secureStorage.delete(key: _accessTokenKey);
    await secureStorage.delete(key: _refreshTokenKey);
    await secureStorage.delete(key: _userIdKey);
    await sharedPreferences.remove(_isLoggedInKey);
  }
  
  @override
  Future<bool> isLoggedIn() async {
    final token = await getAccessToken();
    return token != null && token.isNotEmpty;
  }
  
  @override
  Future<void> saveUserId(String userId) async {
    await secureStorage.write(key: _userIdKey, value: userId);
  }
  
  @override
  Future<String?> getUserId() async {
    return await secureStorage.read(key: _userIdKey);
  }
}
