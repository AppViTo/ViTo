#!/bin/bash

# ViTo Production Deploy Script
# This script deploys the application to Kubernetes

set -e

echo "🚀 Deploying ViTo to Kubernetes..."

# Configuration
NAMESPACE="vito"
REGISTRY="ghcr.io"
IMAGE_PREFIX="vito"
VERSION="${1:-latest}"

# Check prerequisites
echo "📋 Checking prerequisites..."

if ! command -v kubectl &> /dev/null; then
    echo "❌ kubectl is not installed. Please install kubectl first."
    exit 1
fi

if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    exit 1
fi

# Build Docker images
echo "🐳 Building Docker images..."

services=(
    "backend/api-gateway"
    "backend/auth-service"
    "backend/social-service"
    "backend/event-service"
    "backend/checkin-service"
    "backend/mosaic-service"
    "backend/streaming-service"
    "backend/archive-service"
    "backend/chat-service"
    "backend/media-service"
    "ai-services/transcode-service"
    "ai-services/recommendation-service"
    "ai-services/moderation-service"
)

for service in "${services[@]}"; do
    if [ -d "$service" ]; then
        service_name=$(basename $service)
        echo "  Building $service_name..."
        
        if [ -f "$service/Dockerfile" ]; then
            docker build -t $REGISTRY/$IMAGE_PREFIX/$service_name:$VERSION $service
        else
            echo "    ⚠️  No Dockerfile found, skipping"
        fi
    fi
done

# Push images to registry
echo "📤 Pushing images to registry..."

for service in "${services[@]}"; do
    if [ -d "$service" ]; then
        service_name=$(basename $service)
        echo "  Pushing $service_name..."
        docker push $REGISTRY/$IMAGE_PREFIX/$service_name:$VERSION
    fi
done

# Create namespace if it doesn't exist
echo "🏗️  Setting up Kubernetes namespace..."
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

# Apply secrets
echo "🔐 Applying secrets..."
kubectl apply -f infrastructure/kubernetes/base/namespace.yaml

# Deploy services
echo "🚀 Deploying services..."

for manifest in infrastructure/kubernetes/base/*.yaml; do
    if [[ $manifest != *"namespace.yaml"* ]]; then
        echo "  Applying $manifest..."
        kubectl apply -f $manifest
    fi
done

# Wait for deployments
echo "⏳ Waiting for deployments to be ready..."

deployments=(
    "api-gateway"
    "auth-service"
    "chat-service"
    "media-service"
)

for deployment in "${deployments[@]}"; do
    echo "  Waiting for $deployment..."
    kubectl rollout status deployment/$deployment -n $NAMESPACE --timeout=300s
done

# Get service URLs
echo ""
echo "✅ Deployment complete!"
echo ""
echo "🌐 Service URLs:"

# Get API Gateway URL
API_GATEWAY_URL=$(kubectl get ingress api-gateway -n $NAMESPACE -o jsonpath='{.spec.rules[0].host}' 2>/dev/null || echo "api.vito.com")
echo "  - API Gateway: https://$API_GATEWAY_URL"

# Get LoadBalancer IPs
echo ""
echo "📊 Deployed services:"
kubectl get pods -n $NAMESPACE
echo ""
kubectl get services -n $NAMESPACE
echo ""
kubectl get ingress -n $NAMESPACE

echo ""
echo "📝 Useful commands:"
echo "  - Check pods: kubectl get pods -n $NAMESPACE"
echo "  - Check logs: kubectl logs -f deployment/api-gateway -n $NAMESPACE"
echo "  - Port forward: kubectl port-forward svc/api-gateway 8080:80 -n $NAMESPACE"
echo "  - Scale deployment: kubectl scale deployment/api-gateway --replicas=3 -n $NAMESPACE"
