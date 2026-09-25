FROM gcc:15

RUN apt-get update && apt-get install -y \
    libcurl4-openssl-dev \
    libpq-dev \
    make \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY . .

RUN make

CMD ["./bin/health-checker"]