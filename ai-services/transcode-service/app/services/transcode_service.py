from fastapi import UploadFile
from typing import Optional
import asyncio
import subprocess
import os
import uuid
from datetime import datetime

from app.schemas.transcode import TranscodeStatus, TranscodeJob
from app.infrastructure.storage.s3_client import S3Client
from app.infrastructure.kafka.producer import KafkaProducer

class TranscodeService:
    def __init__(self):
        self.s3_client = S3Client()
        self.kafka_producer = KafkaProducer()
        self.jobs = {}  # In-memory job storage (use Redis in production)
    
    async def transcode_video(
        self,
        job_id: str,
        file: UploadFile,
        output_format: str = "mp4",
        quality: str = "medium"
    ):
        """Transcode a video file."""
        try:
            # Update job status
            self.jobs[job_id] = TranscodeJob(
                job_id=job_id,
                input_url="",
                status="processing",
                output_format=output_format,
                quality=quality
            )
            
            # Save uploaded file temporarily
            temp_input = f"/tmp/{job_id}_input.{file.filename.split('.')[-1]}"
            temp_output = f"/tmp/{job_id}_output.{output_format}"
            
            with open(temp_input, "wb") as buffer:
                content = await file.read()
                buffer.write(content)
            
            # Build FFmpeg command based on quality
            ffmpeg_cmd = self._build_ffmpeg_command(
                temp_input, temp_output, output_format, quality
            )
            
            # Run FFmpeg
            process = await asyncio.create_subprocess_exec(
                *ffmpeg_cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE
            )
            stdout, stderr = await process.communicate()
            
            if process.returncode != 0:
                raise Exception(f"FFmpeg error: {stderr.decode()}")
            
            # Upload to S3
            output_key = f"transcoded/{job_id}/{job_id}_output.{output_format}"
            await self.s3_client.upload_file(temp_output, output_key)
            
            # Generate presigned URL
            output_url = await self.s3_client.get_presigned_url(output_key)
            
            # Update job status
            self.jobs[job_id].status = "completed"
            self.jobs[job_id].output_url = output_url
            self.jobs[job_id].progress = 100.0
            
            # Publish completion event
            await self.kafka_producer.publish(
                topic="media-events",
                key=job_id,
                value={
                    "eventType": "VIDEO_TRANSCODED",
                    "jobId": job_id,
                    "outputUrl": output_url,
                    "status": "completed"
                }
            )
            
            # Cleanup temp files
            os.remove(temp_input)
            os.remove(temp_output)
            
        except Exception as e:
            self.jobs[job_id].status = "failed"
            self.jobs[job_id].error_message = str(e)
            
            # Publish failure event
            await self.kafka_producer.publish(
                topic="media-events",
                key=job_id,
                value={
                    "eventType": "TRANSCODE_FAILED",
                    "jobId": job_id,
                    "error": str(e),
                    "status": "failed"
                }
            )
    
    def _build_ffmpeg_command(
        self,
        input_path: str,
        output_path: str,
        output_format: str,
        quality: str
    ) -> list:
        """Build FFmpeg command based on quality settings."""
        base_cmd = ["ffmpeg", "-i", input_path]
        
        # Quality presets
        quality_presets = {
            "low": {"resolution": "640x360", "bitrate": "1000k", "preset": "ultrafast"},
            "medium": {"resolution": "1280x720", "bitrate": "2500k", "preset": "fast"},
            "high": {"resolution": "1920x1080", "bitrate": "5000k", "preset": "medium"}
        }
        
        preset = quality_presets.get(quality, quality_presets["medium"])
        
        # Video codec settings
        base_cmd.extend([
            "-vf", f"scale={preset['resolution']}",
            "-c:v", "libx264",
            "-preset", preset["preset"],
            "-b:v", preset["bitrate"],
            "-c:a", "aac",
            "-b:a", "128k",
            "-movflags", "+faststart"
        ])
        
        base_cmd.append(output_path)
        
        return base_cmd
    
    async def get_job_status(self, job_id: str) -> Optional[TranscodeStatus]:
        """Get the status of a transcoding job."""
        job = self.jobs.get(job_id)
        if not job:
            return None
        
        return TranscodeStatus(
            job_id=job.job_id,
            status=job.status,
            progress=job.progress,
            output_url=job.output_url,
            error_message=job.error_message,
            created_at=datetime.now().isoformat(),
            completed_at=datetime.now().isoformat() if job.status == "completed" else None
        )
    
    async def generate_thumbnail(
        self,
        file: UploadFile,
        timestamp: float = 0.0
    ) -> str:
        """Generate a thumbnail from a video file."""
        job_id = str(uuid.uuid4())
        temp_input = f"/tmp/{job_id}_input.{file.filename.split('.')[-1]}"
        temp_output = f"/tmp/{job_id}_thumbnail.jpg"
        
        try:
            # Save uploaded file
            with open(temp_input, "wb") as buffer:
                content = await file.read()
                buffer.write(content)
            
            # Generate thumbnail
            ffmpeg_cmd = [
                "ffmpeg",
                "-i", temp_input,
                "-ss", str(timestamp),
                "-vframes", "1",
                "-vf", "scale=640:-1",
                "-q:v", "2",
                temp_output
            ]
            
            process = await asyncio.create_subprocess_exec(
                *ffmpeg_cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE
            )
            await process.communicate()
            
            # Upload to S3
            thumbnail_key = f"thumbnails/{job_id}/thumbnail.jpg"
            await self.s3_client.upload_file(temp_output, thumbnail_key)
            
            # Get presigned URL
            thumbnail_url = await self.s3_client.get_presigned_url(thumbnail_key)
            
            return thumbnail_url
            
        finally:
            # Cleanup
            if os.path.exists(temp_input):
                os.remove(temp_input)
            if os.path.exists(temp_output):
                os.remove(temp_output)
