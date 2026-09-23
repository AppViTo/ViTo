from fastapi import APIRouter, UploadFile, File, HTTPException, BackgroundTasks
from fastapi.responses import JSONResponse
from typing import Optional
import uuid

from app.schemas.transcode import TranscodeRequest, TranscodeResponse, TranscodeStatus
from app.services.transcode_service import TranscodeService

router = APIRouter()
transcode_service = TranscodeService()

@router.post("/transcode", response_model=TranscodeResponse)
async def transcode_video(
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...),
    output_format: Optional[str] = "mp4",
    quality: Optional[str] = "medium"
):
    """
    Upload and transcode a video file.
    
    - **file**: Video file to transcode
    - **output_format**: Output format (mp4, webm, avi)
    - **quality**: Output quality (low, medium, high)
    """
    job_id = str(uuid.uuid4())
    
    # Validate file type
    allowed_types = ["video/mp4", "video/avi", "video/quicktime", "video/x-msvideo", "video/webm"]
    if file.content_type not in allowed_types:
        raise HTTPException(
            status_code=400,
            detail=f"Invalid file type. Allowed types: {', '.join(allowed_types)}"
        )
    
    # Start transcoding in background
    background_tasks.add_task(
        transcode_service.transcode_video,
        job_id=job_id,
        file=file,
        output_format=output_format,
        quality=quality
    )
    
    return TranscodeResponse(
        job_id=job_id,
        status="queued",
        message="Transcoding job queued successfully"
    )

@router.get("/transcode/{job_id}/status", response_model=TranscodeStatus)
async def get_transcode_status(job_id: str):
    """
    Get the status of a transcoding job.
    
    - **job_id**: The unique job identifier
    """
    status = await transcode_service.get_job_status(job_id)
    if not status:
        raise HTTPException(status_code=404, detail="Job not found")
    return status

@router.post("/thumbnail")
async def generate_thumbnail(
    file: UploadFile = File(...),
    timestamp: Optional[float] = 0.0
):
    """
    Generate a thumbnail from a video file.
    
    - **file**: Video file
    - **timestamp**: Timestamp to capture thumbnail (in seconds)
    """
    allowed_types = ["video/mp4", "video/avi", "video/quicktime"]
    if file.content_type not in allowed_types:
        raise HTTPException(
            status_code=400,
            detail=f"Invalid file type. Allowed types: {', '.join(allowed_types)}"
        )
    
    thumbnail_url = await transcode_service.generate_thumbnail(file, timestamp)
    return {"thumbnail_url": thumbnail_url}

@router.get("/health")
async def health_check():
    return {"status": "healthy", "service": "transcode-service"}
