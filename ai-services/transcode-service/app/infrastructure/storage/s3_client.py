import boto3
from botocore.config import Config
from botocore.exceptions import ClientError
from typing import Optional
import os

from app.core.config import settings

class S3Client:
    def __init__(self, settings=settings):
        self.endpoint_url = settings.S3_ENDPOINT
        self.bucket_name = settings.S3_BUCKET
        self.access_key = settings.S3_ACCESS_KEY
        self.secret_key = settings.S3_SECRET_KEY
        
        self.s3_client = boto3.client(
            's3',
            endpoint_url=self.endpoint_url,
            aws_access_key_id=self.access_key,
            aws_secret_access_key=self.secret_key,
            region_name=settings.S3_REGION,
            # Mismo estilo de URL para operaciones y URLs firmadas (SeaweedFS exige path)
            config=Config(
                signature_version='s3v4',
                s3={'addressing_style': 'path' if settings.S3_PATH_STYLE else 'virtual'},
            ),
        )
    
    async def upload_file(self, file_path: str, key: str) -> bool:
        """Upload a file to S3."""
        try:
            self.s3_client.upload_file(
                file_path,
                self.bucket_name,
                key,
                ExtraArgs={'ACL': 'public-read'}
            )
            return True
        except ClientError as e:
            print(f"Error uploading file to S3: {e}")
            return False
    
    async def upload_fileobj(self, file_obj, key: str) -> bool:
        """Upload a file object to S3."""
        try:
            self.s3_client.upload_fileobj(
                file_obj,
                self.bucket_name,
                key,
                ExtraArgs={'ACL': 'public-read'}
            )
            return True
        except ClientError as e:
            print(f"Error uploading file object to S3: {e}")
            return False
    
    async def get_presigned_url(self, key: str, expiration: int = 3600) -> str:
        """Generate a presigned URL for a file."""
        try:
            url = self.s3_client.generate_presigned_url(
                'get_object',
                Params={'Bucket': self.bucket_name, 'Key': key},
                ExpiresIn=expiration
            )
            return url
        except ClientError as e:
            print(f"Error generating presigned URL: {e}")
            return ""
    
    async def delete_file(self, key: str) -> bool:
        """Delete a file from S3."""
        try:
            self.s3_client.delete_object(
                Bucket=self.bucket_name,
                Key=key
            )
            return True
        except ClientError as e:
            print(f"Error deleting file from S3: {e}")
            return False
    
    async def list_files(self, prefix: str = "") -> list:
        """List files in S3 bucket with optional prefix."""
        try:
            response = self.s3_client.list_objects_v2(
                Bucket=self.bucket_name,
                Prefix=prefix
            )
            return [obj['Key'] for obj in response.get('Contents', [])]
        except ClientError as e:
            print(f"Error listing files in S3: {e}")
            return []
