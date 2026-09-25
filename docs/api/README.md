# ViTo API Documentation

> ## ⚠️ DOCUMENTO OBSOLETO — NO USAR COMO CONTRATO
>
> Este archivo tiene ~17 endpoints documentados que **no existen** con esos paths
> (`/social/unfollow/{id}`, `/events/user`, `/mosaic/upload`, `/streaming/{id}/join`,
> `/archive/events/{eventId}/share`, `/transcode/video`, …) y le faltan 6 dominios
> completos (social, checkin, mosaic, streaming, archive, todos los WebSockets).
>
> **La fuente de verdad es [`docs/api/openapi.yaml`](./openapi.yaml)** (+ [`asyncapi.yaml`](./asyncapi.yaml)).
> Las reglas de formato están en **[`CONVENTIONS.md`](./CONVENTIONS.md)**.
> Las decisiones ya tomadas están en **[`../decisions/README.md`](../decisions/README.md)**.
>
> Se conserva **solo como referencia histórica** (puertos por servicio, ejemplos de
> payloads y la sección de rate limiting). Ver `docs/decisions/adr-010`.

## Overview

ViTo is a social event lifecycle platform that integrates event creation, digital invitations, RSVP, live mosaic, streaming, and post-event archiving. This documentation covers all API endpoints across the microservices architecture.

## Base URL

All API requests go through the **API Gateway**:

```
http://localhost:8080
```

## Authentication

ViTo uses JWT (JSON Web Tokens) for authentication. Include the token in the `Authorization` header:

```
Authorization: Bearer <your_jwt_token>
```

### Getting a Token

1. Register a new user: `POST /api/v1/auth/register`
2. Login: `POST /api/v1/auth/login`
3. Use the returned `accessToken` for authenticated requests

### Token Refresh

When your token expires, use the refresh token:

```
POST /api/v1/auth/refresh
{
  "refreshToken": "<your_refresh_token>"
}
```

## API Services

| Service | Port | Description |
|---------|------|-------------|
| [Auth Service](#auth-service) | 8081 | User authentication and JWT management |
| [Social Service](#social-service) | 8082 | Social graph and user relationships |
| [Event Service](#event-service) | 8083 | Event lifecycle management |
| [Check-In Service](#checkin-service) | 8084 | QR code generation and check-in validation |
| [Mosaic Service](#mosaic-service) | 8085 | Live mosaic grid and feed |
| [Streaming Service](#streaming-service) | 8086 | Live streaming and reactions |
| [Archive Service](#archive-service) | 8087 | Post-event archiving |
| [Chat Service](#chat-service) | 8088 | Real-time messaging |
| [Media Service](#media-service) | 8089 | File upload and media management |
| [Transcode Service](#transcode-service) | 8000 | Video transcoding (Python) |
| [Recommendation Service](#recommendation-service) | 8001 | AI recommendations (Python) |
| [Moderation Service](#moderation-service) | 8002 | Content moderation (Python) |

---

## Auth Service

Base URL: `/api/v1/auth`

### Register

```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "securePassword123"
}
```

**Response (201 Created):**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": "user123",
    "username": "johndoe",
    "email": "john@example.com"
  }
}
```

### Login

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "john@example.com",
  "password": "securePassword123"
}
```

**Response (200 OK):**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": "user123",
    "username": "johndoe",
    "email": "john@example.com"
  }
}
```

### Refresh Token

```http
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### Logout

```http
POST /api/v1/auth/logout
Authorization: Bearer <token>
```

### Get Current User

```http
GET /api/v1/auth/me
Authorization: Bearer <token>
```

---

## Social Service

Base URL: `/api/v1/social`

### Follow User

```http
POST /api/v1/social/follow/{userId}
Authorization: Bearer <token>
```

### Unfollow User

```http
DELETE /api/v1/social/unfollow/{userId}
Authorization: Bearer <token>
```

### Get Followers

```http
GET /api/v1/social/followers/{userId}
Authorization: Bearer <token>
```

**Response (200 OK):**

```json
{
  "followers": [
    {
      "id": "user456",
      "username": "janedoe",
      "followedAt": "2024-01-15T10:30:00Z"
    }
  ],
  "total": 1,
  "page": 0,
  "size": 20
}
```

### Get Following

```http
GET /api/v1/social/following/{userId}
Authorization: Bearer <token>
```

### Get User Profile

```http
GET /api/v1/social/profile/{userId}
Authorization: Bearer <token>
```

---

## Event Service

Base URL: `/api/v1/events`

### Create Event

```http
POST /api/v1/events
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Summer Music Festival",
  "description": "Annual music festival",
  "date": "2024-07-15T18:00:00Z",
  "endDate": "2024-07-15T23:59:00Z",
  "location": {
    "name": "Central Park",
    "address": "123 Main St",
    "city": "New York",
    "lat": 40.7829,
    "lng": -73.9654
  },
  "isPublic": true,
  "maxAttendees": 500,
  "category": "music"
}
```

**Response (201 Created):**

```json
{
  "id": "event123",
  "name": "Summer Music Festival",
  "description": "Annual music festival",
  "date": "2024-07-15T18:00:00Z",
  "endDate": "2024-07-15T23:59:00Z",
  "location": {
    "name": "Central Park",
    "address": "123 Main St",
    "city": "New York",
    "lat": 40.7829,
    "lng": -73.9654
  },
  "organizerId": "user123",
  "isPublic": true,
  "maxAttendees": 500,
  "currentAttendees": 0,
  "category": "music",
  "status": "ACTIVE",
  "createdAt": "2024-01-20T12:00:00Z"
}
```

### Get Event

```http
GET /api/v1/events/{eventId}
Authorization: Bearer <token>
```

### Update Event

```http
PUT /api/v1/events/{eventId}
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Summer Music Festival 2024",
  "description": "Updated description"
}
```

### Delete Event

```http
DELETE /api/v1/events/{eventId}
Authorization: Bearer <token>
```

### List User Events

```http
GET /api/v1/events/user?page=0&size=20
Authorization: Bearer <token>
```

### Send Invitation

```http
POST /api/v1/events/{eventId}/invitations
Authorization: Bearer <token>
Content-Type: application/json

{
  "userId": "user456",
  "message": "You're invited!"
}
```

### Respond to RSVP

```http
POST /api/v1/events/{eventId}/rsvp
Authorization: Bearer <token>
Content-Type: application/json

{
  "status": "ACCEPTED",
  "guestsCount": 2,
  "dietaryRestrictions": "Vegetarian"
}
```

---

## Check-In Service

Base URL: `/api/v1/checkin`

### Generate QR Ticket

```http
POST /api/v1/checkin/tickets
Authorization: Bearer <token>
Content-Type: application/json

{
  "eventId": "event123",
  "userId": "user456"
}
```

**Response (201 Created):**

```json
{
  "id": "ticket123",
  "eventId": "event123",
  "userId": "user456",
  "qrCode": "data:image/png;base64,...",
  "ticketCode": "VITO-EVT123-USR456",
  "expiresAt": "2024-07-15T23:59:00Z",
  "status": "ACTIVE"
}
```

### Validate Check-In

```http
POST /api/v1/checkin/validate
Authorization: Bearer <token>
Content-Type: application/json

{
  "ticketCode": "VITO-EVT123-USR456",
  "eventId": "event123"
}
```

**Response (200 OK):**

```json
{
  "valid": true,
  "message": "Check-in successful",
  "attendee": {
    "userId": "user456",
    "username": "janedoe",
    "checkedInAt": "2024-07-15T18:30:00Z"
  }
}
```

### Get Event Check-In Stats

```http
GET /api/v1/checkin/stats/{eventId}
Authorization: Bearer <token>
```

---

## Mosaic Service

Base URL: `/api/v1/mosaic`

### Upload to Mosaic

```http
POST /api/v1/mosaic/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data

file: <image_or_video>
eventId: event123
caption: "Great moment!"
```

### Get Mosaic Grid

```http
GET /api/v1/mosaic/grid/{eventId}?page=0&size=50
Authorization: Bearer <token>
```

**Response (200 OK):**

```json
{
  "items": [
    {
      "id": "item123",
      "eventId": "event123",
      "userId": "user456",
      "mediaUrl": "https://s3.example.com/media/item123.jpg",
      "thumbnailUrl": "https://s3.example.com/media/thumb_item123.jpg",
      "caption: "Great moment!",
      "likesCount": 15,
      "createdAt": "2024-07-15T19:30:00Z"
    }
  ],
  "total": 150,
  "page": 0,
  "size": 50
}
```

### Get Mosaic Feed

```http
GET /api/v1/mosaic/feed/{eventId}?page=0&size=20
Authorization: Bearer <token>
```

### Like Mosaic Item

```http
POST /api/v1/mosaic/{itemId}/like
Authorization: Bearer <token>
```

---

## Streaming Service

Base URL: `/api/v1/streaming`

### Start Stream

```http
POST /api/v1/streaming/start
Authorization: Bearer <token>
Content-Type: application/json

{
  "eventId": "event123",
  "title": "Live Performance",
  "description": "Streaming the main stage"
}
```

**Response (201 Created):**

```json
{
  "id": "stream123",
  "eventId": "event123",
  "hostId": "user123",
  "title": "Live Performance",
  "status": "LIVE",
  "streamUrl": "rtmp://stream.example.com/live/stream123",
  "playbackUrl": "https://play.example.com/stream123.m3u8",
  "startedAt": "2024-07-15T20:00:00Z",
  "viewerCount": 0
}
```

### Join Stream

```http
POST /api/v1/streaming/{streamId}/join
Authorization: Bearer <token>
```

### Send Reaction

```http
POST /api/v1/streaming/{streamId}/reactions
Authorization: Bearer <token>
Content-Type: application/json

{
  "type": "HEART",
  "emoji": "❤️"
}
```

### End Stream

```http
POST /api/v1/streaming/{streamId}/end
Authorization: Bearer <token>
```

### Get Stream Stats

```http
GET /api/v1/streaming/{streamId}/stats
Authorization: Bearer <token>
```

---

## Archive Service

Base URL: `/api/v1/archive`

### Get Event Archive

```http
GET /api/v1/archive/events/{eventId}
Authorization: Bearer <token>
```

**Response (200 OK):**

```json
{
  "id": "archive123",
  "eventId": "event123",
  "title": "Summer Music Festival 2024",
  "description": "A night to remember",
  "media": [
    {
      "id": "media123",
      "type": "IMAGE",
      "url": "https://s3.example.com/archive/media123.jpg",
      "caption: "Opening ceremony"
    }
  ],
  "highlights": [
    "Best performance of the year",
    "Record attendance"
  ],
  "stats": {
    "totalAttendees": 450,
    "totalPhotos": 230,
    "totalVideos": 45
  },
  "createdAt": "2024-07-16T10:00:00Z"
}
```

### Add Comment

```http
POST /api/v1/archive/events/{eventId}/comments
Authorization: Bearer <token>
Content-Type: application/json

{
  "content": "Amazing event!"
}
```

### Get Comments

```http
GET /api/v1/archive/events/{eventId}/comments?page=0&size=20
Authorization: Bearer <token>
```

### Share Archive

```http
POST /api/v1/archive/events/{eventId}/share
Authorization: Bearer <token>
Content-Type: application/json

{
  "userId": "user789",
  "message": "Check out this event!"
}
```

---

## Chat Service

Base URL: `/api/v1/chat`

### Create Chat Room

```http
POST /api/v1/chat/rooms
Authorization: Bearer <token>
Content-Type: application/json

{
  "eventId": "event123",
  "name": "Event Chat",
  "type": "EVENT_CHAT",
  "participantIds": ["user456", "user789"]
}
```

### Join Room

```http
POST /api/v1/chat/rooms/{roomId}/join
Authorization: Bearer <token>
```

### Leave Room

```http
POST /api/v1/chat/rooms/{roomId}/leave
Authorization: Bearer <token>
```

### Send Message

```http
POST /api/v1/chat/messages
Authorization: Bearer <token>
Content-Type: application/json

{
  "roomId": "room123",
  "content": "Hello everyone!",
  "type": "TEXT"
}
```

**Response (201 Created):**

```json
{
  "id": "msg123",
  "roomId": "room123",
  "senderId": "user123",
  "senderName": "johndoe",
  "content": "Hello everyone!",
  "type": "TEXT",
  "timestamp": "2024-07-15T19:30:00Z",
  "deleted": false
}
```

### Get Messages

```http
GET /api/v1/chat/rooms/{roomId}/messages?page=0&size=50
Authorization: Bearer <token>
```

### Mark as Read

```http
POST /api/v1/chat/rooms/{roomId}/read
Authorization: Bearer <token>
```

### WebSocket Connection

```javascript
const ws = new WebSocket('ws://localhost:8088/ws/chat?userId=user123');

// Join a room
ws.send(JSON.stringify({
  type: 'JOIN_ROOM',
  roomId: 'room123'
}));

// Send a message
ws.send(JSON.stringify({
  type: 'SEND_MESSAGE',
  roomId: 'room123',
  content: 'Hello!',
  messageType: 'TEXT'
}));

// Send typing indicator
ws.send(JSON.stringify({
  type: 'TYPING',
  roomId: 'room123',
  isTyping: true
}));

// Listen for messages
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log('Received:', data);
};
```

---

## Media Service

Base URL: `/api/v1/media`

### Upload Media

```http
POST /api/v1/media/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data

file: <image/video>
eventId: event123
```

**Response (201 Created):**

```json
{
  "id": "media123",
  "eventId": "event123",
  "uploadedBy": "user123",
  "fileName": "abc123.jpg",
  "originalFileName": "photo.jpg",
  "contentType": "image/jpeg",
  "fileSize": 1024000,
  "status": "UPLOADED",
  "metadata": {
    "width": 1920,
    "height": 1080,
    "format": "jpeg"
  },
  "location": {
    "bucket": "vito-media",
    "key": "events/event123/user123/abc123.jpg",
    "url": "http://localhost:9000/vito-media/events/event123/user123/abc123.jpg",
    "thumbnailUrl": "http://localhost:9000/vito-media/events/event123/user123/thumb_abc123.jpg"
  },
  "uploadedAt": "2024-07-15T19:30:00Z"
}
```

### Get Media

```http
GET /api/v1/media/{mediaId}
Authorization: Bearer <token>
```

### Get Event Media

```http
GET /api/v1/media/event/{event123}?type=IMAGE
Authorization: Bearer <token>
```

### Get Presigned URL

```http
GET /api/v1/media/{mediaId}/presigned-url?expirationMinutes=60
Authorization: Bearer <token>
```

### Delete Media

```http
DELETE /api/v1/media/{mediaId}
Authorization: Bearer <token>
```

---

## Transcode Service (Python)

Base URL: `/api/v1/transcode`

### Transcode Video

```http
POST /api/v1/transcode/video
Authorization: Bearer <token>
Content-Type: application/json

{
  "inputUrl": "http://s3.example.com/videos/input.mp4",
  "outputFormats": ["mp4", "hls"],
  "qualities": ["720p", "1080p"]
}
```

### Get Transcoding Status

```http
GET /api/v1/transcode/status/{jobId}
Authorization: Bearer <token>
```

---

## Recommendation Service (Python)

Base URL: `/api/v1/recommendations`

### Get Event Recommendations

```http
POST /api/v1/recommendations/events
Authorization: Bearer <token>
Content-Type: application/json

{
  "user_id": "user123",
  "limit": 10
}
```

**Response (200 OK):**

```json
[
  {
    "event_id": "event456",
    "event_name": "Jazz Night",
    "score": 0.85,
    "reason": "Matches your interest in music",
    "category": "music",
    "date": "2024-08-01T20:00:00Z"
  }
]
```

### Get User Recommendations

```http
POST /api/v1/recommendations/users
Authorization: Bearer <token>
Content-Type: application/json

{
  "user_id": "user123",
  "limit": 10
}
```

### Get Trending Events

```http
GET /api/v1/recommendations/trending?limit=10
Authorization: Bearer <token>
```

### Get Similar Events

```http
GET /api/v1/recommendations/similar/{eventId}?limit=5
Authorization: Bearer <token>
```

---

## Moderation Service (Python)

Base URL: `/api/v1/moderation`

### Check Content

```http
POST /api/v1/moderation/check
Authorization: Bearer <token>
Content-Type: application/json

{
  "content_id": "msg123",
  "content_type": "text",
  "content_text": "This is a test message",
  "user_id": "user123"
}
```

**Response (200 OK):**

```json
{
  "content_id": "msg123",
  "is_safe": true,
  "confidence": 0.95,
  "flags": [],
  "categories": [],
  "recommended_action": "approve",
  "details": {
    "content_type": "text",
    "user_id": "user123",
    "moderated_at": "2024-07-15T19:30:00Z"
  }
}
```

### Report Content

```http
POST /api/v1/moderation/report
Authorization: Bearer <token>
Content-Type: application/json

{
  "content_id": "msg123",
  "content_type": "text",
  "reason": "spam",
  "reporter_id": "user456",
  "details": "This is spam content"
}
```

### Get Moderation Stats

```http
GET /api/v1/moderation/stats/{eventId}
Authorization: Bearer <token>
```

---

## Error Responses

All endpoints follow a consistent error response format:

```json
{
  "error": "Error type",
  "message": "Detailed error message",
  "timestamp": "2024-07-15T19:30:00Z"
}
```

### Common HTTP Status Codes

| Code | Description |
|------|-------------|
| 200 | Success |
| 201 | Created |
| 400 | Bad Request - Invalid input |
| 401 | Unauthorized - Missing or invalid token |
| 403 | Forbidden - Insufficient permissions |
| 404 | Not Found - Resource doesn't exist |
| 409 | Conflict - Resource already exists |
| 422 | Unprocessable Entity - Validation error |
| 429 | Too Many Requests - Rate limit exceeded |
| 500 | Internal Server Error |

---

## Rate Limiting

The API Gateway implements rate limiting per user:

| Endpoint | Rate Limit | Burst |
|----------|------------|-------|
| Auth endpoints | 10 req/s | 20 |
| Event endpoints | 20 req/s | 40 |
| Other endpoints | 10 req/s | 20 |

When rate limit is exceeded:

```json
{
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Please try again later."
}
```

---

## WebSocket Events

### Chat Events

```json
// Incoming message
{
  "type": "MESSAGE",
  "data": {
    "id": "msg123",
    "roomId": "room123",
    "senderId": "user456",
    "content": "Hello!",
    "timestamp": "2024-07-15T19:30:00Z"
  }
}

// Typing indicator
{
  "type": "TYPING",
  "data": {
    "roomId": "room123",
    "userId": "user456",
    "isTyping": true
  }
}

// User joined
{
  "type": "USER_JOINED",
  "data": {
    "roomId": "room123",
    "userId": "user789"
  }
}
```

---

## SDKs and Code Examples

### cURL Examples

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@example.com","password":"password123"}'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}'

# Create Event
curl -X POST http://localhost:8080/api/v1/events \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"My Event","date":"2024-12-31T20:00:00Z"}'
```

### JavaScript (Fetch)

```javascript
// Login
const response = await fetch('http://localhost:8080/api/v1/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'test@example.com', password: 'password123' })
});
const { accessToken } = await response.json();

// Create Event
await fetch('http://localhost:8080/api/v1/events', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ name: 'My Event', date: '2024-12-31T20:00:00Z' })
});
```

### Dart (Flutter)

```dart
// Login
final response = await http.post(
  Uri.parse('http://localhost:8080/api/v1/auth/login'),
  headers: {'Content-Type': 'application/json'},
  body: jsonEncode({'email': 'test@example.com', 'password': 'password123'}),
);
final data = jsonDecode(response.body);
final token = data['accessToken'];

// Create Event
await http.post(
  Uri.parse('http://localhost:8080/api/v1/events'),
  headers: {
    'Authorization': 'Bearer $token',
    'Content-Type': 'application/json',
  },
  body: jsonEncode({'name': 'My Event', 'date': '2024-12-31T20:00:00Z'}),
);
```
