from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

from app.core.config import settings
from app.api.v1.router import router as api_router
from app.services.recommendation_engine import RecommendationEngine

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    app.state.recommendation_engine = RecommendationEngine()
    yield
    # Shutdown

app = FastAPI(
    title="ViTo Recommendation Service",
    description="AI-powered recommendation engine for events, content, and users",
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

app.include_router(api_router, prefix="/api/v1/recommendations")

@app.get("/health")
async def health_check():
    return {"status": "healthy", "service": "recommendation-service"}

@app.get("/actuator/health")
async def actuator_health():
    return {"status": "UP", "components": {"recommendationEngine": {"status": "UP"}}}
