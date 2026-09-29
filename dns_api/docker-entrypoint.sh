#!/bin/sh
# Arranque del DNS API. Con SSL_ENABLED=true genera un keystore autofirmado si aún no existe
# (o usa el que se monte en SSL_KEYSTORE_PATH) y sirve HTTPS en SERVER_PORT.
set -e

if [ "${SSL_ENABLED:-false}" = "true" ]; then
  : "${SSL_KEYSTORE_PATH:=/tmp/dns-api-keystore.p12}"
  : "${SSL_KEYSTORE_PASSWORD:?SSL_KEYSTORE_PASSWORD es obligatorio cuando SSL_ENABLED=true}"
  CN="${SSL_CERT_CN:-dns-api}"
  export SSL_KEYSTORE_PATH
  if [ ! -f "$SSL_KEYSTORE_PATH" ]; then
    keytool -genkeypair -alias dns-api -keyalg RSA -keysize 2048 -storetype PKCS12 \
      -keystore "$SSL_KEYSTORE_PATH" -storepass "$SSL_KEYSTORE_PASSWORD" -keypass "$SSL_KEYSTORE_PASSWORD" \
      -validity 365 -dname "CN=$CN" -ext "SAN=dns:$CN,dns:localhost,ip:127.0.0.1"
  fi
fi

exec java -jar /app/app.jar
