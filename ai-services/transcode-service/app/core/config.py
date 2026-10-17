from pydantic_settings import BaseSettings
from typing import List
import os

class Settings(BaseSettings):
    PROJECT_NAME: str = "ViTo Transcode Service"
    VERSION: str = "1.0.0"
    API_V1_PREFIX: str = "/api/v1"
    
    # CORS
    CORS_ORIGINS: List[str] = ["*"]
    
    # MongoDB
    MONGODB_URI: str  # obligatorio (env)
    MONGODB_DB_NAME: str = os.getenv("MONGODB_DB_NAME", "vito_db")
    
    # Redis
    REDIS_URL: str = os.getenv("REDIS_URL", "redis://localhost:6379")
    
    # Kafka
    KAFKA_BOOTSTRAP_SERVERS: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    # Producción (docker-compose.prod.yml): SASL_PLAINTEXT + PLAIN. En local, sin auth.
    KAFKA_SECURITY_PROTOCOL: str = "PLAINTEXT"
    KAFKA_SASL_MECHANISM: str = "PLAIN"
    KAFKA_SASL_USERNAME: str = ""
    KAFKA_SASL_PASSWORD: str = ""
    
    # S3/MinIO
    S3_ENDPOINT: str = os.getenv("S3_ENDPOINT", "http://localhost:9000")
    S3_BUCKET: str = os.getenv("S3_BUCKET", "vito-media")
    S3_ACCESS_KEY: str  # obligatorio (env)
    S3_SECRET_KEY: str  # obligatorio (env)
    
    # FFmpeg
    FFMPEG_PATH: str = os.getenv("FFMPEG_PATH", "/usr/bin/ffmpeg")
    FFPROBE_PATH: str = os.getenv("FFPROBE_PATH", "/usr/bin/ffprobe")
    
    # Transcoding
    TRANSCODE_OUTPUT_DIR: str = "/tmp/transcode"
    THUMBNAIL_OUTPUT_DIR: str = "/tmp/thumbnails"
    
    class Config:
        env_file = ".env"
        case_sensitive = True

settings = Settings()
