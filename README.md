# ViTo (VibeTogether)

Plataforma de ciclo de vida de eventos sociales que integra invitaciones digitales, RSVP, mosaico en vivo y streaming.

## 🚀 Stack Tecnológico

- **Frontend:** Flutter (Dart) - Clean Architecture Feature-First
- **Backend:** Java Spring Boot - Arquitectura Hexagonal
- **IA/Transcode:** Python FastAPI
- **Database:** MongoDB + Redis
- **Message Broker:** Apache Kafka
- **Streaming:** SRS / Ant Media Server

## 📁 Estructura del Proyecto

```
vito/
├── backend/           # Microservicios Spring Boot
├── ai-services/       # Servicios Python FastAPI
├── frontend/          # Aplicación Flutter
├── infrastructure/    # Docker, Kubernetes, scripts
└── docs/              # Documentación
```

## 🏃‍♂️ Inicio Rápido

### Requisitos Previos
- Docker & Docker Compose
- Java 17+
- Python 3.11+
- Flutter 3.2+

### Levantar Stack Local

```bash
# Clonar repositorio
git clone <repository-url>
cd vito

# Levantar servicios base
docker-compose up -d

# Correr auth-service
cd backend/auth-service
./mvnw spring-boot:run

# Correr Flutter
cd frontend/vito_app
flutter pub get
flutter run
```

## 📋 Plan de Trabajo

Ver [AGENTS.md](AGENTS.md) para el plan completo de desarrollo.

### Fases MVP

| Fase | Descripción | Timeline |
|------|-------------|----------|
| 0 | Setup & Infraestructura | Semanas 0-1 |
| 1 | Auth & User Management | Semanas 2-3 |
| 2 | Social Graph | Semana 4 |
| 3 | Eventos & Invitaciones | Semanas 5-6 |
| 4 | QR & Check-in | Semana 7 |
| 5 | Mosaico en Vivo | Semanas 8-9 |
| 6 | Streaming | Semana 10 |
| 7 | Post-Evento | Semana 11 |
| 8 | Polish & MVP Release | Semana 12 |

## 🎯 KPIs MVP

- Conversión invitado → registro: > 70%
- RSVP completado: > 80%
- Check-in exitoso QR: > 95%
- Latencia mosaico en vivo: < 3s
- QR validation time: < 200ms

## 📄 Licencia

Propietario - Todos los derechos reservados.
