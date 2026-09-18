CC      = gcc
CFLAGS  = -Wall -Wextra -O2 $(shell pkg-config --cflags libcurl)
LDFLAGS = $(shell pkg-config --libs libcurl) -lpthread
SRC     = src/main.c src/config.c src/supabase.c src/checks.c src/cJSON.c
BIN     = health-checker

.PHONY: all clean run

all: $(BIN)

$(BIN): $(SRC)
	$(CC) $(CFLAGS) -o $(BIN) $(SRC) $(LDFLAGS)

run: $(BIN)
	./$(BIN)

clean:
	rm -f $(BIN)