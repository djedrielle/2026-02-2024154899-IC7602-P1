CC = gcc
CFLAGS = -Wall -Wextra -O2 -I/usr/include/postgresql
LIBS = -lcurl -lpthread -lpq

SRC = src/main.c src/checkers.c src/db_client.c
OUT = bin/health-checker

all: build

build:
	mkdir -p bin
	$(CC) $(CFLAGS) $(SRC) -o $(OUT) $(LIBS)

clean:
	rm -rf bin