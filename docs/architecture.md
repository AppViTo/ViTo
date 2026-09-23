# ViTo Architecture Documentation

## System Overview

ViTo is a microservices-based social event lifecycle platform built with a modern tech stack:

- **Frontend**: Flutter (Dart)
- **Backend Core**: Java Spring Boot
- **AI Services**: Python FastAPI
- **Database**: MongoDB
- **Cache**: Redis
- **Message Broker**: Apache Kafka
- **Media Storage**: AWS S3/MinIO
- **Streaming**: SRS (Simple Realtime Server)

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT LAYER                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ Flutter App  │  │   Web App    │  │  Admin Panel │  │   Mobile     │  │
│  │  (iOS/Android)│  │  (React)    │  │   (Vue.js)  │  │  (React Native)│  │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           API GATEWAY LAYER                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     Spring Cloud Gateway                           │   │
│  │  • JWT Authentication  • Rate Limiting  • Circuit Breaker          │   │
│  │  • Load Balancing      • Request Routing • SSL Termination         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         MICROSERVICES LAYER                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     CORE SERVICES (Java Spring Boot)                │   │
│  │                                                                     │   │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐  │   │
│  │  │   Auth     │  │   Social   │  │   Events   │  │  Check-In  │  │   │
│  │  │  Service   │  │  Service   │  │  Service   │  │  Service   │  │   │
│  │  │  (8081)   │  │  (8082)   │  │  (8083)   │  │  (8084)   │  │   │
│  │  └────────────┘  └────────────┘  └────────────┘  └────────────┘  │   │
│  │                                                                     │   │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐  │   │
│  │  │  Mosaic    │  │ Streaming  │  │  Archive   │  │    Chat    │  │   │
│  │  │  Service   │  │  Service   │  │  Service   │  │  Service   │  │   │
│  │  │  (8085)   │  │  (8086)   │  │  (8087)   │  │  (8088)   │  │   │
│  │  └────────────┘  └────────────┘  └────────────┘  └────────────┘  │   │
│  │                                                                     │   │
│  │  ┌────────────┐                                                    │   │
│  │  │   Media    │                                                    │   │
│  │  │  Service   │                                                    │   │
│  │  │  (8089)   │                                                    │   │
│  │  └────────────┘                                                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    AI SERVICES (Python FastAPI)                      │   │
│  │                                                                     │   │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐                   │   │
│  │  │ Transcode  │  │Recommend-  │  │ Moderation │                   │   │
│  │  │  Service   │  │ ation Svc  │  │  Service   │                   │   │
│  │  │  (8000)   │  │  (8001)   │  │  (8002)   │                   │   │
│  │  └────────────┘  └────────────┘  └────────────┘                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          DATA LAYER                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐              │
│  │    MongoDB     │  │     Redis      │  │     Kafka      │              │
│  │   (Database)   │  │ (Cache/PubSub) │  │  (Messaging)   │              │
│  │    27017       │  │     6379       │  │     9092       │              │
│  └────────────────┘  └────────────────┘  └────────────────┘              │
│                                                                             │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐              │
│  │  MinIO / S3    │  │  SRS Server    │  │  Zookeeper     │              │
│  │ (Object Store) │  │  (Streaming)   │  │  (Coordination)│              │
│  │     9000       │  │   1985/1935    │  │     2181       │              │
│  └────────────────┘  └────────────────┘  └────────────────┘              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Service Communication

### Synchronous Communication (REST)

```
Client → API Gateway → Microservice
                        ↓
                    Other Microservice (via REST)
```

### Asynchronous Communication (Kafka)

```
Microservice A → Kafka Topic → Microservice B
                    ↓
                Consumer Group
```

### Real-time Communication (WebSocket)

```
Client ←→ WebSocket Server ←→ Redis Pub/Sub ←→ Other Clients
```

## Data Flow Examples

### 1. User Registration Flow

```
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│ Client  │    │ Gateway │    │  Auth   │    │ MongoDB │
└────┬────┘    └────┬────┘    └────┬────┘    └────┬────┘
     │ POST /register │            │              │
     │───────────────→│            │              │
     │                │ Forward    │              │
     │                │───────────→│              │
     │                │            │ Create User  │
     │                │            │─────────────→│
     │                │            │              │
     │                │            │ JWT Token    │
     │                │            │←─────────────│
     │                │ Response   │              │
     │                │←───────────│              │
     │ Access Token   │            │              │
     │←───────────────│            │              │
```

### 2. Event Creation & Invitation Flow

```
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│ Client  │    │ Gateway │    │ Events  │    │  Kafka  │
└────┬────┘    └────┬────┘    └────┬────┘    └────┬────┘
     │ POST /events │            │              │
     │───────────────→│            │              │
     │                │ Create     │              │
     │                │───────────→│              │
     │                │            │ Publish      │
     │                │            │─────────────→│
     │                │            │              │
     │                │            │              │→ Notification Service
     │                │            │              │→ Email Service
     │ Event Created  │            │              │
     │←───────────────│            │              │
```

### 3. Real-time Chat Flow

```
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│ Client A│    │  Chat   │    │  Redis  │    │ Client B│
└────┬────┘    └────┬────┘    └────┬────┘    └────┬────┘
     │ WS Connect   │            │              │
     │───────────────→│            │              │
     │                │ Subscribe  │              │
     │                │───────────→│              │
     │                │            │              │
     │ Send Message   │            │              │
     │───────────────→│            │              │
     │                │ Store      │              │
     │                │───────────→│              │
     │                │ Publish    │              │
     │                │───────────→│              │
     │                │            │ Broadcast    │
     │                │            │─────────────→│
     │                │            │              │
     │ Ack            │            │              │
     │←───────────────│            │              │
```

## Database Schema

### MongoDB Collections

```javascript
// users collection
{
  _id: ObjectId,
  username: String,
  email: String,
  password: String (hashed),
  profile: {
    displayName: String,
    bio: String,
    avatar: String,
    coverImage: String,
    location: {
      type: String,
      coordinates: [Number],
      city: String,
      country: String
    }
  },
  stats: {
    followersCount: Number,
    followingCount: Number,
    postsCount: Number,
    streamsCount: Number
  },
  settings: {
    notifications: Object,
    privacy: Object
  },
  createdAt: Date,
  updatedAt: Date
}

// events collection
{
  _id: ObjectId,
  name: String,
  description: String,
  date: Date,
  endDate: Date,
  location: {
    name: String,
    address: String,
    city: String,
    coordinates: [Number]
  },
  organizerId: ObjectId,
  isPublic: Boolean,
  maxAttendees: Number,
  currentAttendees: Number,
  category: String,
  status: String,
  createdAt: Date,
  updatedAt: Date
}

// chat_messages collection
{
  _id: ObjectId,
  roomId: String,
  senderId: ObjectId,
  senderName: String,
  content: String,
  type: String,
  mediaUrl: String,
  timestamp: Date,
  editedAt: Date,
  deleted: Boolean
}

// media collection
{
  _id: ObjectId,
  eventId: ObjectId,
  uploadedBy: ObjectId,
  fileName: String,
  originalFileName: String,
  contentType: String,
  fileSize: Number,
  status: String,
  metadata: {
    width: Number,
    height: Number,
    duration: Number,
    format: String
  },
  location: {
    bucket: String,
    key: String,
    url: String,
    thumbnailUrl: String
  },
  uploadedAt: Date,
  processedAt: Date
}
```

### Redis Keys

```
# User session
session:{userId} → JWT token
token:blacklist:{token} → true

# Chat
chat:room:{roomId} → Set of online users
chat:typing:{roomId} → Typing indicators
chat:messages:{roomId} → Recent messages cache

# Rate limiting
ratelimit:{endpoint}:{userId} → Counter

# Cache
cache:event:{eventId} → Event data
cache:user:{userId} → User data
```

## Security Architecture

### Authentication Flow

```
1. User registers/logs in
2. Auth Service validates credentials
3. Auth Service generates JWT token
4. Token contains: userId, email, roles, expiry
5. Client stores token securely
6. Client sends token in Authorization header
7. Gateway validates token signature
8. Gateway extracts user info and forwards to service
```

### JWT Token Structure

```json
{
  "header": {
    "alg": "HS256",
    "typ": "JWT"
  },
  "payload": {
    "sub": "user123",
    "email": "user@example.com",
    "roles": ["USER"],
    "iat": 1700000000,
    "exp": 1700086400
  }
}
```

## Deployment Architecture

### Docker Compose (Development)

```yaml
services:
  mongodb: 27017
  redis: 6379
  kafka: 9092
  zookeeper: 2181
  srs: 1985, 1935
  minio: 9000, 9001
```

### Kubernetes (Production)

```
┌─────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster                    │
├─────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────┐   │
│  │  Ingress Controller (nginx)                      │   │
│  └─────────────────────────────────────────────────┘   │
│                          │                              │
│  ┌─────────────────────────────────────────────────┐   │
│  │  API Gateway (2 replicas)                        │   │
│  └─────────────────────────────────────────────────┘   │
│                          │                              │
│  ┌─────────────────────────────────────────────────┐   │
│  │  Core Services (2 replicas each)                 │   │
│  │  Auth | Social | Events | CheckIn | Mosaic      │   │
│  │  Streaming | Archive | Chat | Media              │   │
│  └─────────────────────────────────────────────────┘   │
│                          │                              │
│  ┌─────────────────────────────────────────────────┐   │
│  │  AI Services (1 replica each)                    │   │
│  │  Transcode | Recommendation | Moderation         │   │
│  └─────────────────────────────────────────────────┘   │
│                          │                              │
│  ┌─────────────────────────────────────────────────┐   │
│  │  StatefulSets (Persistent Storage)               │   │
│  │  MongoDB | Redis | Kafka | MinIO                 │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

## Monitoring & Observability

### Health Checks

Each service exposes:
- `/actuator/health/liveness` - Is the service alive?
- `/actuator/health/readiness` - Is the service ready to accept traffic?

### Metrics (Prometheus)

```
- HTTP request duration
- HTTP request count
- JVM memory usage
- Thread pool usage
- Kafka consumer lag
- Redis connection pool
```

### Logging

Structured JSON logging with:
- Request ID
- User ID
- Service name
- Timestamp
- Log level
- Message

### Distributed Tracing (Jaeger)

- Trace requests across services
- Identify bottlenecks
- Debug errors

## Performance Considerations

### Caching Strategy

1. **Redis Cache**: User sessions, frequently accessed data
2. **Kafka**: Async processing, event streaming
3. **CDN**: Static assets, media files

### Database Optimization

1. **MongoDB Indexes**: Optimized queries for each collection
2. **Connection Pooling**: Efficient database connections
3. **Read Replicas**: Scale read operations

### API Optimization

1. **Pagination**: All list endpoints support pagination
2. **Compression**: Gzip enabled
3. **Rate Limiting**: Prevent abuse
4. **Circuit Breaker**: Fault tolerance

## Disaster Recovery

### Backup Strategy

1. **MongoDB**: Daily backups with point-in-time recovery
2. **Redis**: AOF persistence + RDB snapshots
3. **Kafka**: Replicated topics with retention
4. **S3/MinIO**: Versioning enabled

### Recovery Procedures

1. **Service Failure**: Auto-restart via Kubernetes
2. **Database Failure**: Failover to replica
3. **Region Failure**: Multi-region deployment

## Scalability

### Horizontal Scaling

- All services are stateless
- Kubernetes HPA for auto-scaling
- Kafka partitions for parallel processing

### Vertical Scaling

- MongoDB sharding for large datasets
- Redis cluster for high throughput
- S3 for unlimited storage

## Security Best Practices

1. **Secrets Management**: Kubernetes Secrets / Vault
2. **Network Policies**: Service-to-service communication
3. **Pod Security**: Non-root containers, read-only filesystem
4. **Image Scanning**: Vulnerability detection
5. **RBAC**: Role-based access control
6. **Audit Logging**: Track all mutations
