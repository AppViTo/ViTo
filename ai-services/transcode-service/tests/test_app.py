import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.core.config import Settings
from app.main import app

# Sin context manager: no se ejecuta el lifespan, así que no hace falta Kafka.
client = TestClient(app)


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "healthy", "service": "transcode-service"}


def test_metrics_endpoint_exposes_http_metrics():
    client.get("/health")
    response = client.get("/metrics")
    assert response.status_code == 200
    assert "http_requests_total" in response.text


def test_root_exposes_service_info():
    response = client.get("/")
    assert response.status_code == 200
    assert response.json()["service"] == "transcode-service"


def test_transcode_rejects_non_video_files():
    response = client.post(
        "/api/v1/transcode",
        files={"file": ("notes.txt", b"hello", "text/plain")},
    )
    assert response.status_code == 400


@pytest.mark.parametrize("missing", ["MONGODB_URI", "S3_ACCESS_KEY", "S3_SECRET_KEY"])
def test_settings_require_secrets(monkeypatch, missing):
    monkeypatch.delenv(missing, raising=False)
    with pytest.raises(ValidationError):
        Settings(_env_file=None)
