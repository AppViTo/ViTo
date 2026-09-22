class ServerException implements Exception {
  final String message;
  final int? statusCode;
  
  const ServerException({
    this.message = 'Server error occurred',
    this.statusCode,
  });
}

class CacheException implements Exception {
  final String message;
  
  const CacheException({
    this.message = 'Cache error occurred',
  });
}

class NetworkException implements Exception {
  final String message;
  
  const NetworkException({
    this.message = 'Network error occurred',
  });
}

class ValidationException implements Exception {
  final String message;
  
  const ValidationException({
    this.message = 'Validation error occurred',
  });
}

class AuthenticationException implements Exception {
  final String message;
  
  const AuthenticationException({
    this.message = 'Authentication failed',
  });
}

class AuthorizationException implements Exception {
  final String message;
  
  const AuthorizationException({
    this.message = 'Authorization failed',
  });
}
