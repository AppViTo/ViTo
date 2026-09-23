from pydantic import BaseModel
from typing import Optional, List
from enum import Enum

class OutputFormat(str, Enum):
    MP4 = "mp4"
    WEBM = "webm"
    AVI = "avi"
    MOV = "mov"

class Quality(str, Enum):
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"

class TranscodeRequest(BaseModel):
    output_format: Optional[OutputFormat] = OutputFormat.MP4
    quality: Optional[Quality] = Quality.MEDIUM
    resolution: Optional[str] = None  # e.g., "1920x1080"
    bitrate: Optional[str] = None  # e.g., "5000k"

class TranscodeResponse(BaseModel):
    job_id: str
    status: str
    message: str

class TranscodeStatus(BaseModel):
    job_id: str
    status: str  # queued, processing, completed, failed
    progress: Optional[float] = None  # 0-100
    output_url: Optional[str] = None
    error_message: Optional[str] = None
    created_at: Optional[str] = None
    completed_at: Optional[str] = None

class TranscodeJob(BaseModel):
    job_id: str
    input_url: str
    output_url: Optional[str] = None
    status: str
    progress: float = 0.0
    output_format: str
    quality: str
    error_message: Optional[str] = None
