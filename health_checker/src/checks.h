#ifndef CHECKS_H
#define CHECKS_H

/*
 * Parámetros necesarios para verificar un target.
 * Agrupa en una sola estructura lo que antes se pasaba como una lista larga
 * de argumentos, para que el despachador (run_check) reciba un único puntero.
 */
typedef struct {
    const char* host;            // IP o hostname del target
    int port;                    // Puerto a verificar
    const char* check_type;      // "HTTP" usa check_http; cualquier otro valor usa check_tcp
    int timeout_ms;              // Tiempo máximo por intento, en milisegundos
    const char* http_path;       // Ruta HTTP (solo aplica para checks HTTP)
    const char* expected_codes;  // Códigos aceptados, p. ej. "{200,301}" (solo HTTP)
    const char* basic_auth_user; // Usuario de Basic Auth; vacío = sin autenticación
    const char* basic_auth_pass; // Contraseña de Basic Auth
} check_params_t;

/* Check HTTP: devuelve 1 si la petición HEAD responde con un código esperado. */
int check_http(
    const char* host,
    int port,
    const char* path,
    const char* expected_codes,
    const char* basic_auth_user,
    const char* basic_auth_pass,
    int timeout_ms,
    double* latency
);

/* Check TCP: devuelve 1 si se logra abrir una conexión al host:puerto. */
int check_tcp(
    const char* host,
    int port,
    int timeout_ms,
    double* latency
);

/* Ejecuta el check que corresponda según params->check_type. */
int run_check(const check_params_t* params, double* latency);

#endif
