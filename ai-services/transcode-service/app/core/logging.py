import logging
import time

from pythonjsonlogger.json import JsonFormatter


def configure_logging(service_name: str) -> None:
    """Configura logs JSON con los campos que exige T-I13/T053."""
    logging.Formatter.converter = time.gmtime
    root = logging.getLogger()
    if not any(getattr(handler, "_vito_json", False) for handler in root.handlers):
        handler = logging.StreamHandler()
        handler.setFormatter(
            JsonFormatter(
                "%(asctime)s %(levelname)s %(name)s %(message)s",
                datefmt="%Y-%m-%dT%H:%M:%S%z",
                rename_fields={"asctime": "time", "levelname": "level"},
                static_fields={"service": service_name},
            )
        )
        handler._vito_json = True
        root.addHandler(handler)
    root.setLevel(logging.INFO)
