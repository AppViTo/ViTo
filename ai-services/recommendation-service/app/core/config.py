from pydantic_settings import BaseSettings
from typing import Optional

class Settings(BaseSettings):
    APP_NAME: str = "ViTo Recommendation Service"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = False
    
    # MongoDB
    MONGODB_URI: str  # obligatorio (env)
    MONGODB_DB_NAME: str = "vito_db"
    
    # Redis
    REDIS_URL: str = "redis://localhost:6379"
    
    # Kafka
    KAFKA_BOOTSTRAP_SERVERS: str = "localhost:9092"
    
    # JWT
    JWT_SECRET: str  # obligatorio (env)
    
    # Recommendation settings
    MAX_RECOMMENDATIONS: int = 10
    SIMILARITY_THRESHOLD: float = 0.3
    CONTENT_WEIGHT: float = 0.4
    SOCIAL_WEIGHT: float = 0.3
    TRENDING_WEIGHT: float = 0.3

    class Config:
        env_file = ".env"
        case_sensitive = True

settings = Settings()
