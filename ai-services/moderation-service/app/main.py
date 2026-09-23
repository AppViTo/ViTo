from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

from app.core.config import settings
from app.api.v1.router import router as api_router
from app.services.moderation_engine import ModerationEngine

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    app.state.moderation_engine = ModerationEngine()
    yield
    # Shutdown

app = FastAPI(
    title="ViTo Moderation Service",
    description="Content moderation and safety service for ViTo platform",
    version="1.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000", "http://localhost:8080"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router, prefix="/api/v1/moderation")

@app.get("/health")
async def health_check():
    return {"status": "healthy", "service": "moderation-service"}

@app.get("/actuator/health")
async def actuator_health():
    return {"status": "UP", "components": {"moderationEngine": {"status": "UP"}}}
