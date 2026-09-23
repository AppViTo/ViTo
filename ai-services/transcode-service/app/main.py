from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
import logging

from app.core.config import settings
from app.api.v1.router import router as api_router
from app.infrastructure.kafka.consumer import KafkaConsumer
from app.infrastructure.kafka.producer import KafkaProducer

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

kafka_consumer = KafkaConsumer()
kafka_producer = KafkaProducer()

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    logger.info("Starting Transcode Service...")
    await kafka_consumer.start()
    await kafka_producer.start()
    yield
    # Shutdown
    logger.info("Shutting down Transcode Service...")
    await kafka_consumer.stop()
    await kafka_producer.stop()

app = FastAPI(
    title="ViTo Transcode Service",
    description="Video and image transcoding microservice for ViTo",
    version="1.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router, prefix="/api/v1")

@app.get("/health")
async def health_check():
    return {"status": "healthy", "service": "transcode-service"}

@app.get("/")
async def root():
    return {
        "service": "transcode-service",
        "version": "1.0.0",
        "docs": "/docs"
    }
