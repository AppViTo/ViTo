from pydantic_settings import BaseSettings
from typing import Optional

class Settings(BaseSettings):
    APP_NAME: str = "ViTo Moderation Service"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = False
    
    # MongoDB
    MONGODB_URI: str = "mongodb://admin:vito_password@localhost:27017/vito_db?authSource=admin"
    MONGODB_DB_NAME: str = "vito_db"
    
    # Redis
    REDIS_URL: str = "redis://localhost:6379"
    
    # Kafka
    KAFKA_BOOTSTRAP_SERVERS: str = "localhost:9092"
    
    # JWT
    JWT_SECRET: str = "vito-super-secret-jwt-key-change-in-production-2024"
    
    # Moderation settings
    AUTO_MODERATE: bool = True
    CONFIDENCE_THRESHOLD: float = 0.8
    MAX_REPORTS_BEFORE_BAN: int = 5
    BAN_DURATION_DAYS: int = 7
    
    # Content filters
    PROFANITY_FILTER: bool = True
    NSFW_FILTER: bool = True
    SPAM_FILTER: bool = True

    class Config:
        env_file = ".env"
        case_sensitive = True

settings = Settings()
