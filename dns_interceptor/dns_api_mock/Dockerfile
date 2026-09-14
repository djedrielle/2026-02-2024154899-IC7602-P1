FROM python:3.11-slim

WORKDIR /app

# openssl para generar el certificado self-signed (HTTPS)
RUN apt-get update \
    && apt-get install -y --no-install-recommends openssl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Certificado autofirmado para servir HTTPS (solo desarrollo)
RUN openssl req -x509 -newkey rsa:2048 -nodes \
    -keyout /app/key.pem -out /app/cert.pem \
    -days 365 -subj "/CN=dns_api_mock"

EXPOSE 8443

CMD ["uvicorn", "app:app", \
     "--host", "0.0.0.0", "--port", "8443", \
     "--ssl-keyfile", "/app/key.pem", \
     "--ssl-certfile", "/app/cert.pem"]
