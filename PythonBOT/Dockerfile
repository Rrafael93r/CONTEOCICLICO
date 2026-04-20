# ---------- Etapa 1: Dependencias del sistema + Python ----------
FROM python:3.11-slim

# Evitar buffering de stdout (logs se ven inmediatamente en Docker)
ENV PYTHONUNBUFFERED=1

# Dependencias de sistema necesarias para Playwright/Chromium
RUN apt-get update && apt-get install -y --no-install-recommends \
    libnss3 \
    libatk1.0-0 \
    libatk-bridge2.0-0 \
    libcups2 \
    libdrm2 \
    libxkbcommon0 \
    libxcomposite1 \
    libxdamage1 \
    libxfixes3 \
    libxrandr2 \
    libgbm1 \
    libasound2 \
    libpango-1.0-0 \
    libcairo2 \
    libatspi2.0-0 \
    curl \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# ---------- Etapa 2: Dependencias de Python ----------
WORKDIR /app

RUN pip install --no-cache-dir \
    playwright \
    python-dotenv \
    paramiko \
    fastapi \
    uvicorn \
    apscheduler

# Instalar los navegadores de Playwright (solo Chromium para reducir tamaño)
RUN playwright install chromium
RUN playwright install-deps chromium

# ---------- Etapa 3: Copiar código ----------
COPY bot_medicar.py .
COPY api.py .

# Crear carpeta de descarga dentro del contenedor (se puede montar como volumen)
RUN mkdir -p /app/downloads

# ---------- Etapa 4: Puerto y ejecución ----------
EXPOSE 8000

CMD ["uvicorn", "api:app", "--host", "0.0.0.0", "--port", "8000"]
