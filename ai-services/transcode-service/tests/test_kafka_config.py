from app.core.config import Settings
from app.infrastructure.kafka.client_config import kafka_client_config


def test_local_kafka_without_auth(monkeypatch):
    monkeypatch.delenv("KAFKA_SECURITY_PROTOCOL", raising=False)
    config = kafka_client_config(Settings(_env_file=None))

    assert config["security.protocol"] == "PLAINTEXT"
    assert "sasl.username" not in config


def test_production_kafka_with_sasl(monkeypatch):
    monkeypatch.setenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
    monkeypatch.setenv("KAFKA_SECURITY_PROTOCOL", "SASL_PLAINTEXT")
    monkeypatch.setenv("KAFKA_SASL_USERNAME", "vito")
    monkeypatch.setenv("KAFKA_SASL_PASSWORD", "secret")

    config = kafka_client_config(Settings(_env_file=None))

    assert config == {
        "bootstrap.servers": "kafka:9092",
        "security.protocol": "SASL_PLAINTEXT",
        "sasl.mechanism": "PLAIN",
        "sasl.username": "vito",
        "sasl.password": "secret",
    }
