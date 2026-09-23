from fastapi import APIRouter, Depends, HTTPException, status, Request
from typing import List, Optional
from pydantic import BaseModel
from datetime import datetime

router = APIRouter()

class ContentModerationRequest(BaseModel):
    content_id: str
    content_type: str  # text, image, video
    content_text: Optional[str] = None
    content_url: Optional[str] = None
    user_id: str
    event_id: Optional[str] = None

class ModerationResult(BaseModel):
    content_id: str
    is_safe: bool
    confidence: float
    flags: List[str]
    categories: List[str]
    recommended_action: str
    details: dict

class ReportRequest(BaseModel):
    content_id: str
    content_type: str
    reason: str
    reporter_id: str
    details: Optional[str] = None

class ReportResponse(BaseModel):
    report_id: str
    status: str
    message: str

class ModerationStats(BaseModel):
    total_moderated: int
    flagged_count: int
    approved_count: int
    rejected_count: int
    average_confidence: float

@router.post("/check", response_model=ModerationResult)
async def moderate_content(request: ContentModerationRequest):
    engine = request.app.state.moderation_engine
    result = await engine.moderate_content(
        content_id=request.content_id,
        content_type=request.content_type,
        content_text=request.content_text,
        content_url=request.content_url,
        user_id=request.user_id
    )
    return result

@router.post("/report", response_model=ReportResponse)
async def report_content(request: ReportRequest):
    engine = request.app.state.moderation_engine
    result = await engine.report_content(
        content_id=request.content_id,
        content_type=request.content_type,
        reason=request.reason,
        reporter_id=request.reporter_id,
        details=request.details
    )
    return result

@router.get("/stats/{event_id}", response_model=ModerationStats)
async def get_moderation_stats(event_id: str):
    # Placeholder for stats endpoint
    return ModerationStats(
        total_moderated=0,
        flagged_count=0,
        approved_count=0,
        rejected_count=0,
        average_confidence=0.0
    )

@router.get("/reports")
async def get_reports(
    status: Optional[str] = None,
    limit: int = 50,
    offset: int = 0
):
    # Placeholder for reports list
    return {"reports": [], "total": 0}

@router.put("/reports/{report_id}/resolve")
async def resolve_report(
    report_id: str,
    action: str,  # approve, reject, ban
    moderator_id: str,
    reason: Optional[str] = None
):
    # Placeholder for report resolution
    return {"status": "resolved", "report_id": report_id, "action": action}
