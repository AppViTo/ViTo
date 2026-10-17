from app.core.config import Settings
from app.infrastructure.storage.s3_client import S3Client


def _settings(monkeypatch, **env):
    for key, value in env.items():
        monkeypatch.setenv(key, value)
    return Settings(_env_file=None)


def test_defaults_target_local_seaweedfs(monkeypatch):
    monkeypatch.delenv("S3_REGION", raising=False)
    monkeypatch.delenv("S3_PATH_STYLE", raising=False)
    s3 = S3Client(_settings(monkeypatch))

    assert s3.s3_client.meta.region_name == "us-east-1"
    assert s3.s3_client.meta.config.s3["addressing_style"] == "path"


def test_r2_settings_from_env(monkeypatch):
    s3 = S3Client(_settings(
        monkeypatch,
        S3_ENDPOINT="https://account.r2.cloudflarestorage.com",
        S3_REGION="auto",
        S3_BUCKET="vito-media",
        S3_PATH_STYLE="false",
    ))

    assert s3.s3_client.meta.endpoint_url == "https://account.r2.cloudflarestorage.com"
    assert s3.s3_client.meta.region_name == "auto"
    assert s3.s3_client.meta.config.s3["addressing_style"] == "virtual"


def test_presigned_url_is_path_style(monkeypatch):
    s3 = S3Client(_settings(monkeypatch, S3_ENDPOINT="http://object-storage:8333"))

    url = s3.s3_client.generate_presigned_url(
        "get_object", Params={"Bucket": "vito-media", "Key": "a/b.mp4"}, ExpiresIn=60
    )

    assert url.startswith("http://object-storage:8333/vito-media/a/b.mp4?")
