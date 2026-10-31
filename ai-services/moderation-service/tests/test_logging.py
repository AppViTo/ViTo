import json
import logging

from app.core.logging import configure_logging


def test_configure_logging_emits_json_with_service(capsys):
    root = logging.getLogger()
    for handler in list(root.handlers):
        if getattr(handler, "_vito_json", False):
            root.removeHandler(handler)

    configure_logging("moderation-service")
    logging.getLogger("vito.test").info("probe")

    lines = [line for line in capsys.readouterr().err.splitlines() if line.startswith("{")]
    payload = json.loads(lines[-1])
    assert payload["service"] == "moderation-service"
    assert payload["message"] == "probe"
    assert payload["level"] == "INFO"
