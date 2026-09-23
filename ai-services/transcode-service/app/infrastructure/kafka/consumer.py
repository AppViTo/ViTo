from confluent_kafka import Consumer, KafkaError, KafkaException
import json
import asyncio
from typing import Callable, Dict

from app.core.config import settings

class KafkaConsumer:
    def __init__(self):
        self.consumer = Consumer({
            'bootstrap.servers': settings.KAFKA_BOOTSTRAP_SERVERS,
            'group.id': 'transcode-service-consumer',
            'auto.offset.reset': 'earliest',
            'enable.auto.commit': True
        })
        self.handlers: Dict[str, Callable] = {}
        self.running = False
    
    def register_handler(self, topic: str, handler: Callable):
        """Register a handler for a topic."""
        self.handlers[topic] = handler
    
    async def start(self):
        """Start consuming messages."""
        self.running = True
        
        if self.handlers:
            self.consumer.subscribe(list(self.handlers.keys()))
            
            while self.running:
                try:
                    msg = self.consumer.poll(1.0)
                    
                    if msg is None:
                        continue
                    
                    if msg.error():
                        if msg.error().code() == KafkaError._PARTITION_EOF:
                            continue
                        else:
                            raise KafkaException(msg.error())
                    
                    # Process message
                    topic = msg.topic()
                    value = json.loads(msg.value().decode('utf-8'))
                    
                    if topic in self.handlers:
                        await self.handlers[topic](value)
                    
                except KafkaException as e:
                    print(f"Kafka error: {e}")
                    await asyncio.sleep(1)
                except Exception as e:
                    print(f"Error processing message: {e}")
    
    async def stop(self):
        """Stop consuming messages."""
        self.running = False
        self.consumer.close()
