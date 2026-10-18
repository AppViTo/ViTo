from fastapi.testclient import TestClient

from app.main import app

# Sin context manager: no se ejecuta el lifespan.
client = TestClient(app)


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "healthy", "service": "moderation-service"}


def test_metrics_endpoint_exposes_http_metrics():
    client.get("/health")
    response = client.get("/metrics")
    assert response.status_code == 200
    assert "http_requests_total" in response.text
