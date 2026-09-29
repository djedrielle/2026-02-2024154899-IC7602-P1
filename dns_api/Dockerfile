# --- Etapa de build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -Dmaven.test.skip=true

# --- Etapa de ejecución ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

# 8080: HTTP. 8443: HTTPS (solo con SSL_ENABLED=true)
EXPOSE 8080 8443

ENTRYPOINT ["/app/docker-entrypoint.sh"]