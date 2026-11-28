FROM python:3.11-slim as builder

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir --user -r requirements.txt

# Runtime stage
FROM python:3.11-slim

WORKDIR /app

# Parches de Debian sobre la base: python:3.11-slim trae libzstd1 1.5.7+dfsg-1 con un CVE HIGH
# ya corregido (CVE-2026-124851) y el Trivy de cd.yml bloquea el release (Principio III)
RUN apt-get update && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/*

# Copy installed packages from builder
COPY --from=builder /root/.local /root/.local
ENV PATH=/root/.local/bin:$PATH

# setuptools y wheel solo hacen falta para instalar; sus copias vendorizadas tienen CVE (Trivy)
RUN pip uninstall -y setuptools wheel

# Copy application code
COPY . .

# Expose port
EXPOSE 8002

# Run the application
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8002", "--reload"]
