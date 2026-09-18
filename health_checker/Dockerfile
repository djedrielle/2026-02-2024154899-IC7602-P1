# --- Etapa de build ---
FROM ubuntu:24.04 AS build
RUN apt-get update && apt-get install -y --no-install-recommends \
        gcc make pkg-config libcurl4-openssl-dev ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY src/ src/
COPY Makefile .
RUN make

# --- Etapa final (imagen mínima) ---
FROM ubuntu:24.04
RUN apt-get update && apt-get install -y --no-install-recommends \
        libcurl4 ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /src/health-checker /usr/local/bin/health-checker

# Todo se configura por variables de entorno (ver README.md);
# no hay valores quemados en la imagen.
ENTRYPOINT ["/usr/local/bin/health-checker"]