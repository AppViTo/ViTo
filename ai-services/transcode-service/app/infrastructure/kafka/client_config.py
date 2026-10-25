def kafka_client_config(settings) -> dict:
    """Conexión común a consumer, producer y admin; SASL solo si el protocolo lo pide."""
    config = {
        'bootstrap.servers': settings.KAFKA_BOOTSTRAP_SERVERS,
        'security.protocol': settings.KAFKA_SECURITY_PROTOCOL,
    }
    if settings.KAFKA_SECURITY_PROTOCOL.startswith('SASL'):
        config.update({
            'sasl.mechanism': settings.KAFKA_SASL_MECHANISM,
            'sasl.username': settings.KAFKA_SASL_USERNAME,
            'sasl.password': settings.KAFKA_SASL_PASSWORD,
        })
    return config
