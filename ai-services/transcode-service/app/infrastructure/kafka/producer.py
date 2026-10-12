from confluent_kafka import Producer
from confluent_kafka.admin import AdminClient, NewTopic
import json
from typing import Any
import os

from app.core.config import settings

class KafkaProducer:
    def __init__(self):
        self.producer = Producer({
            'bootstrap.servers': settings.KAFKA_BOOTSTRAP_SERVERS,
            'client.id': 'transcode-service-producer',
            'acks': 'all'
        })
    
    def _ensure_topics(self):
        """Ensure required topics exist."""
        admin_client = AdminClient({
            'bootstrap.servers': settings.KAFKA_BOOTSTRAP_SERVERS
        })
        
        topics = ['media-events', 'user-events', 'chat-events']
        
        existing_topics = admin_client.list_topics(timeout=10).topics.keys()
        
        new_topics = [
            NewTopic(topic, num_partitions=1, replication_factor=1)
            for topic in topics
            if topic not in existing_topics
        ]
        
        if new_topics:
            admin_client.create_topics(new_topics)
    
    async def start(self):
        """Start the producer. Topics are ensured here (lifespan), not on import,
        so importing the app never requires a reachable broker."""
        self._ensure_topics()
    
    async def stop(self):
        """Stop the producer."""
        self.producer.flush()
    
    async def publish(self, topic: str, key: str, value: Any):
        """Publish a message to a topic."""
        try:
            json_value = json.dumps(value) if isinstance(value, dict) else str(value)
            
            self.producer.produce(
                topic=topic,
                key=key,
                value=json_value,
                callback=self._delivery_callback
            )
            self.producer.poll(0)
            
        except Exception as e:
            print(f"Error publishing message to Kafka: {e}")
            raise
    
    def _delivery_callback(self, err, msg):
        """Callback for message delivery."""
        if err:
            print(f"Message delivery failed: {err}")
        else:
            print(f"Message delivered to {msg.topic()} [{msg.partition()}]")
