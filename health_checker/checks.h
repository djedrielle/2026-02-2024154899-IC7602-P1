/*
 * checks.h — Pruebas de salud TCP y HTTP.
 *
 * Ambas miden el round-trip time (RTT) del intento exitoso, que es lo
 * que alimenta el tipo de registro "round-trip" del DNS Interceptor.
 */
#ifndef CHECKS_H
#define CHECKS_H

#include <stdbool.h>

typedef struct {
    bool   healthy;     /* resultado final tras aplicar retries */
    double rtt_ms;       /* latencia del intento exitoso (-1 si nunca respondió) */
} check_result_t;

/* --- TCP: sólo intenta establecer la conexión (3-way handshake) --- */
check_result_t tcp_health_check(const char *host, int port,
                                 int timeout_ms, int retries);

/* --- HTTP: hace GET a http(s)://host:port/path y valida el código ---
 * expected_codes: arreglo de códigos aceptados (ej. {200,301}); n_codes su tamaño.
 * basic_user/basic_pass: NULL si el target no requiere autenticación. */
check_result_t http_health_check(const char *url, const char *path,
                                  int timeout_ms, int retries,
                                  const int *expected_codes, int n_codes,
                                  const char *basic_user, const char *basic_pass);

#endif