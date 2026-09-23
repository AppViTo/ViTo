from fastapi import APIRouter, Depends, HTTPException, status, Request
from typing import List, Optional
from pydantic import BaseModel

router = APIRouter()

class RecommendationRequest(BaseModel):
    user_id: str
    event_id: Optional[str] = None
    limit: int = 10
    recommendation_type: str = "events"  # events, users, content

class RecommendationResponse(BaseModel):
    id: str
    score: float
    reason: str
    metadata: dict

class EventRecommendation(BaseModel):
    event_id: str
    event_name: str
    score: float
    reason: str
    category: str
    date: str

class UserRecommendation(BaseModel):
    user_id: str
    username: str
    score: float
    reason: str
    mutual_friends: int

class ContentRecommendation(BaseModel):
    content_id: str
    content_type: str
    score: float
    reason: str

@router.post("/events", response_model=List[EventRecommendation])
async def get_event_recommendations(request: RecommendationRequest):
    engine = request.app.state.recommendation_engine
    recommendations = await engine.get_event_recommendations(
        user_id=request.user_id,
        limit=request.limit
    )
    return recommendations

@router.post("/users", response_model=List[UserRecommendation])
async def get_user_recommendations(request: RecommendationRequest):
    engine = request.app.state.recommendation_engine
    recommendations = await engine.get_user_recommendations(
        user_id=request.user_id,
        limit=request.limit
    )
    return recommendations

@router.post("/content", response_model=List[ContentRecommendation])
async def get_content_recommendations(request: RecommendationRequest):
    engine = request.app.state.recommendation_engine
    recommendations = await engine.get_content_recommendations(
        user_id=request.user_id,
        event_id=request.event_id,
        limit=request.limit
    )
    return recommendations

@router.get("/trending")
async def get_trending_events(limit: int = 10):
    engine = request.app.state.recommendation_engine
    trending = await engine.get_trending_events(limit=limit)
    return trending

@router.get("/similar/{event_id}")
async def get_similar_events(event_id: str, limit: int = 5):
    engine = request.app.state.recommendation_engine
    similar = await engine.get_similar_events(event_id=event_id, limit=limit)
    return similar
