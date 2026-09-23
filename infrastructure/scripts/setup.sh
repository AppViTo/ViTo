#!/bin/bash

# ViTo Development Setup Script
# This script sets up the local development environment

set -e

echo "🚀 Setting up ViTo development environment..."

# Check prerequisites
echo "📋 Checking prerequisites..."

if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

if ! command -v java &> /dev/null; then
    echo "⚠️  Java is not installed. Some services may not work."
fi

if ! command -v flutter &> /dev/null; then
    echo "⚠️  Flutter is not installed. Frontend may not work."
fi

# Create .env file if it doesn't exist
if [ ! -f .env ]; then
    echo "📝 Creating .env file from template..."
    cat > .env << EOF
# ViTo Environment Variables

# MongoDB
MONGODB_URI=mongodb://admin:vito_password@localhost:27017/vito_db?authSource=admin
MONGODB_DB_NAME=vito_db

# Redis
REDIS_URL=redis://localhost:6379

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# JWT
JWT_SECRET=vito-super-secret-jwt-key-change-in-production-2024
JWT_EXPIRATION=86400
JWT_REFRESH_EXPIRATION=604800

# S3/MinIO
S3_ENDPOINT=http://localhost:9000
S3_BUCKET=vito-media
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=minioadmin

# SRS Media Server
SRS_API_URL=http://localhost:1985
WEBRTC_STUN_URL=stun:stun.l.google.com:19302

# App URLs
API_BASE_URL=http://localhost:8081
STREAM_BASE_URL=http://localhost:1935

# Debug
LOG_LEVEL=DEBUG
EOF
fi

# Start infrastructure services
echo "🐳 Starting infrastructure services..."
docker-compose up -d mongodb redis kafka zookeeper srs

# Wait for services to be ready
echo "⏳ Waiting for services to be ready..."
sleep 10

# Check if services are running
echo "🔍 Checking service status..."
docker-compose ps

# Build Java services
echo "🔨 Building Java services..."
cd backend

for service in auth-service social-service event-service checkin-service mosaic-service streaming-service archive-service chat-service media-service api-gateway; do
    if [ -d "$service" ]; then
        echo "  Building $service..."
        cd $service
        if [ -f "mvnw" ]; then
            ./mvnw clean package -DskipTests -q
        elif [ -f "pom.xml" ]; then
            mvn clean package -DskipTests -q
        fi
        cd ..
    fi
done

cd ..

# Build Python services
echo "🐍 Building Python services..."
cd ai-services

for service in transcode-service recommendation-service moderation-service; do
    if [ -d "$service" ]; then
        echo "  Building $service..."
        cd $service
        if [ -f "requirements.txt" ]; then
            pip install -r requirements.txt -q
        fi
        cd ..
    fi
done

cd ..

# Setup Flutter
echo "📱 Setting up Flutter..."
cd frontend/vito_app

if command -v flutter &> /dev/null; then
    flutter pub get
else
    echo "⚠️  Flutter not installed, skipping frontend setup"
fi

cd ../..

echo ""
echo "✅ Setup complete!"
echo ""
echo "📚 Next steps:"
echo "  1. Start development tools: docker-compose up -d"
echo "  2. Run auth-service: cd backend/auth-service && ./mvnw spring-boot:run"
echo "  3. Run Flutter app: cd frontend/vito_app && flutter run"
echo ""
echo "🌐 Service URLs:"
echo "  - API Gateway: http://localhost:8080"
echo "  - Auth Service: http://localhost:8081"
echo "  - Mongo Express: http://localhost:8081"
echo "  - Redis Commander: http://localhost:8082"
echo "  - Kafka UI: http://localhost:8083"
echo "  - MinIO Console: http://localhost:9001"
echo ""
