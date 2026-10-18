import os

# Valores solo para tests: la configuración real exige estas variables (sin defaults).
os.environ.setdefault("MONGODB_URI", "mongodb://localhost:27017/test_db")
os.environ.setdefault("JWT_SECRET", "test-secret-key-for-unit-tests-only-256-bits-long")
