#include "checks.h"

#include <arpa/inet.h>
#include <ctype.h>
#include <curl/curl.h>
#include <netdb.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

/* ---------------------------------------------------------------------------
 * Utilidades internas
 * ------------------------------------------------------------------------- */

// Milisegundos transcurridos desde 'start'. Usa el reloj monotónico para que
// los cambios de hora del sistema no afecten la medición de latencia.
static double elapsed_ms(const struct timespec* start) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);

    return (now.tv_sec - start->tv_sec) * 1000.0
         + (now.tv_nsec - start->tv_nsec) / 1000000.0;
}

// Callback de escritura para libcurl que descarta el cuerpo de la respuesta.
// Evita que el HTML de la respuesta se imprima en los logs.
static size_t discard_body(void *contents, size_t size, size_t nmemb, void *userp) {
    (void)contents;
    (void)userp;
    return size * nmemb; // Reportar todo como "consumido" para que curl no aborte
}

// Recorre una lista de códigos en cualquier formato (p. ej. "{200,301}" o
// "200 204") y devuelve 1 si 'status_code' aparece en ella.
// Si no se configuró ninguna lista, solo se acepta 200.
static int status_code_is_expected(long status_code, const char* expected_codes) {
    if (expected_codes == NULL || *expected_codes == '\0') {
        return status_code == 200;
    }

    const char* p = expected_codes;

    while (*p != '\0') {
        // Saltar separadores: llaves, comas, espacios, etc.
        if (!isdigit((unsigned char)*p)) {
            p++;
            continue;
        }

        // strtol lee la secuencia completa de dígitos y deja 'end' justo después
        char* end = NULL;
        long code = strtol(p, &end, 10);

        if (code == status_code) {
            return 1;
        }

        p = end; // Continuar después del número recién leído
    }

    return 0;
}

// Construye "http://host:puerto/ruta" garantizando que exista una sola '/'
// entre el puerto y la ruta. Una ruta vacía o nula se trata como "/".
static void build_http_url(char* buffer, size_t size, const char* host, int port, const char* path) {
    if (path == NULL || *path == '\0') {
        path = "/";
    }

    const char* separator = (path[0] == '/') ? "" : "/";
    snprintf(buffer, size, "http://%s:%d%s%s", host, port, separator, path);
}

// Intenta abrir una conexión TCP contra una dirección ya resuelta.
// El socket se cierra siempre; solo interesa saber si la conexión fue posible.
static int try_tcp_connect(const struct addrinfo* addr, int timeout_ms) {
    int sockfd = socket(addr->ai_family, addr->ai_socktype, addr->ai_protocol);
    if (sockfd < 0) {
        return 0;
    }

    // Convertir el timeout de milisegundos a la estructura segundos/microsegundos
    struct timeval tv;
    tv.tv_sec = timeout_ms / 1000;
    tv.tv_usec = (timeout_ms % 1000) * 1000;

    setsockopt(sockfd, SOL_SOCKET, SO_RCVTIMEO, (const char*)&tv, sizeof(tv));
    setsockopt(sockfd, SOL_SOCKET, SO_SNDTIMEO, (const char*)&tv, sizeof(tv));

    int connected = (connect(sockfd, addr->ai_addr, addr->ai_addrlen) == 0);

    close(sockfd);
    return connected;
}

/* ---------------------------------------------------------------------------
 * Checks públicos
 * ------------------------------------------------------------------------- */

int check_http(
    const char* host,
    int port,
    const char* path,
    const char* expected_codes,
    const char* basic_auth_user,
    const char* basic_auth_pass,
    int timeout_ms,
    double* latency
) {
    *latency = 0.0; // Valor por defecto si no se llega a ejecutar la petición

    // La URL se arma antes de crear el handle; no depende de curl
    char url[512];
    build_http_url(url, sizeof(url), host, port, path);

    CURL *curl = curl_easy_init();
    if (!curl) {
        return 0;
    }

    // Credenciales Basic Auth, solo si el target tiene un usuario configurado
    if (basic_auth_user != NULL && *basic_auth_user != '\0') {
        curl_easy_setopt(curl, CURLOPT_HTTPAUTH, CURLAUTH_BASIC);
        curl_easy_setopt(curl, CURLOPT_USERNAME, basic_auth_user);
        curl_easy_setopt(curl, CURLOPT_PASSWORD, basic_auth_pass != NULL ? basic_auth_pass : "");
    }

    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_NOBODY, 1L);                    // Petición HEAD: solo interesa el código
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);            // Seguir redirecciones
    curl_easy_setopt(curl, CURLOPT_TIMEOUT_MS, (long)timeout_ms);  // Límite total de la petición
    curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);                  // No usar señales (seguro en hilos)
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, discard_body);   // No imprimir el cuerpo

    // Medir únicamente el tiempo de la petición
    struct timespec start;
    clock_gettime(CLOCK_MONOTONIC, &start);
    CURLcode res = curl_easy_perform(curl);
    *latency = elapsed_ms(&start);

    long response_code = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &response_code);
    curl_easy_cleanup(curl);

    // Sano solo si curl no falló Y el código HTTP está en la lista esperada
    return res == CURLE_OK && status_code_is_expected(response_code, expected_codes);
}

int check_tcp(const char* host, int port, int timeout_ms, double* latency) {
    // getaddrinfo recibe el puerto como texto
    char port_str[16];
    snprintf(port_str, sizeof(port_str), "%d", port);

    // Solo IPv4 y sockets de flujo (TCP)
    struct addrinfo hints;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;

    // La latencia TCP incluye la resolución DNS más el intento de conexión
    struct timespec start;
    clock_gettime(CLOCK_MONOTONIC, &start);

    struct addrinfo *addresses = NULL;
    int healthy = 0;

    if (getaddrinfo(host, port_str, &hints, &addresses) == 0) {
        // Probar cada dirección resuelta hasta que una acepte la conexión
        for (struct addrinfo *rp = addresses; rp != NULL && !healthy; rp = rp->ai_next) {
            healthy = try_tcp_connect(rp, timeout_ms);
        }

        freeaddrinfo(addresses);
    }

    // Punto de salida único: la latencia se registra tanto en éxito como en fallo
    *latency = elapsed_ms(&start);
    return healthy;
}

int run_check(const check_params_t* params, double* latency) {
    // "HTTP" es el único tipo especial; cualquier otro tipo se verifica por TCP
    if (strcmp(params->check_type, "HTTP") == 0) {
        return check_http(
            params->host,
            params->port,
            params->http_path,
            params->expected_codes,
            params->basic_auth_user,
            params->basic_auth_pass,
            params->timeout_ms,
            latency
        );
    }

    return check_tcp(params->host, params->port, params->timeout_ms, latency);
}
