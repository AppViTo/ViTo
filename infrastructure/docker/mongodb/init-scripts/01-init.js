// ViTo MongoDB Initialization Script
// This script runs when the container starts for the first time

// Create collections with schema validation
db.createCollection("users", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["username", "email", "createdAt"],
      properties: {
        username: {
          bsonType: "string",
          description: "Username must be a string and is required"
        },
        email: {
          bsonType: "string",
          pattern: "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
          description: "Email must be a valid email address and is required"
        },
        profile: {
          bsonType: "object",
          properties: {
            displayName: { bsonType: "string" },
            bio: { bsonType: "string" },
            avatar: { bsonType: "string" }
          }
        },
        stats: {
          bsonType: "object",
          properties: {
            followersCount: { bsonType: "long" },
            followingCount: { bsonType: "long" },
            postsCount: { bsonType: "long" }
          }
        },
        createdAt: {
          bsonType: "date"
        }
      }
    }
  }
});

// Create indexes
db.users.createIndex({ "username": 1 }, { unique: true });
db.users.createIndex({ "email": 1 }, { unique: true });
db.users.createIndex({ "profile.location": "2dsphere" });

// Create events collection
db.createCollection("events", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["organizerId", "title", "schedule.start", "createdAt"],
      properties: {
        organizerId: { bsonType: "objectId" },
        title: { bsonType: "string" },
        schedule: {
          bsonType: "object",
          required: ["start"],
          properties: {
            start: { bsonType: "date" },
            end: { bsonType: "date" }
          }
        },
        capacity: {
          bsonType: "object",
          properties: {
            max: { bsonType: "int" },
            current: { bsonType: "int" }
          }
        }
      }
    }
  }
});

// Índices de listEvents (ADR-014, T-B07) en la base que usa event-service
// (spring.data.mongodb.database: vito_events), no en la de este script.
// scope=mine: organizerId + createdAt descendente
// scope=upcoming: status + settings.isPublic + details.startDate ascendente
const eventsDb = db.getSiblingDB("vito_events");
eventsDb.events.createIndex({ "organizerId": 1, "createdAt": -1 });
eventsDb.events.createIndex({ "status": 1, "settings.isPublic": 1, "details.startDate": 1 });

// Create posts collection
db.createCollection("posts", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["authorId", "type", "createdAt"],
      properties: {
        authorId: { bsonType: "objectId" },
        type: { 
          bsonType: "string",
          enum: ["image", "video", "text", "carousel"]
        },
        content: {
          bsonType: "object",
          properties: {
            text: { bsonType: "string" },
            hashtags: { bsonType: "array" }
          }
        },
        stats: {
          bsonType: "object",
          properties: {
            likesCount: { bsonType: "long" },
            commentsCount: { bsonType: "long" },
            viewsCount: { bsonType: "long" }
          }
        }
      }
    }
  }
});

db.posts.createIndex({ "authorId": 1, "createdAt": -1 });
db.posts.createIndex({ "createdAt": -1 });
db.posts.createIndex({ "content.hashtags": 1 });

// Create qr_tickets collection
db.createCollection("qr_tickets", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["eventId", "userId", "qrToken", "status"],
      properties: {
        eventId: { bsonType: "objectId" },
        userId: { bsonType: "objectId" },
        status: {
          bsonType: "string",
          enum: ["valid", "used", "expired", "revoked"]
        }
      }
    }
  }
});

db.qr_tickets.createIndex({ "eventId": 1, "userId": 1 }, { unique: true });
db.qr_tickets.createIndex({ "qrToken.payload": 1 });

print("✅ ViTo MongoDB initialization completed!");
