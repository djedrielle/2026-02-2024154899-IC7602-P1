FROM python:3.11-slim

WORKDIR /app

# openssl para generar el certificado self-signed (HTTPS)
RUN apt-get update \
    && apt-get install -y --no-install-recommends openssl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Certificado autofirmado en /certs (FUERA de /app) para que un bind mount
# sobre /app durante el desarrollo no lo oculte. (Solo desarrollo.)
RUN mkdir -p /certs \
    && openssl req -x509 -newkey rsa:2048 -nodes \
       -keyout /certs/key.pem -out /certs/cert.pem \
       -days 365 -subj "/CN=dns_api_mock"

EXPOSE 8443

# --reload recarga al cambiar el código. Se vigilan .py y .json (para que
# editar records.json / ip_to_country.json también recargue).
CMD ["uvicorn", "app:app", \
     "--host", "0.0.0.0", "--port", "8443", \
     "--ssl-keyfile", "/certs/key.pem", \
     "--ssl-certfile", "/certs/cert.pem", \
     "--reload", \
     "--reload-include", "*.py", \
     "--reload-include", "*.json"]
