#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <curl/curl.h>
#include <libpq-fe.h>

#include "checks.h"
#include "database_client.h"

#define DEFAULT_INTERVAL_SECONDS 30

/* Configuración de esta instancia, leída una sola vez al arrancar. */
typedef struct {
    checker_location_t location; // Desde dónde se realizan los checks
    int interval_seconds;        // Pausa entre cada ciclo de verificación
} checker_config_t;

/* Un target tal como se leyó de la base de datos, listo para verificarse. */
typedef struct {
    const char* target_id;
    const char* dns_record_id; // Vacío si el target no está ligado a un registro DNS
    int retries;               // Cantidad de intentos para decidir por mayoría
    check_params_t check;      // Parámetros que consume run_check()
} target_t;

// Devuelve el valor de la variable de entorno, o 'default_value' si no existe
// o está vacía.
static const char* getenv_or_default(const char* name, const char* default_value) {
    const char* value = getenv(name);
    return (value != NULL && *value != '\0') ? value : default_value;
}

// Carga toda la configuración desde el entorno.
// No depende de la base de datos ni de curl, por eso se ejecuta primero.
static checker_config_t load_config(void) {
    checker_config_t config;

    config.location.location_id = getenv_or_default("CHECKER_LOCATION_ID", "CR-01");
    config.location.latitude    = getenv_or_default("CHECKER_LATITUDE", "9.8644");
    config.location.longitude   = getenv_or_default("CHECKER_LONGITUDE", "-83.9194");
    config.location.country     = getenv_or_default("CHECKER_COUNTRY", "CR");
    config.location.city        = getenv_or_default("CHECKER_CITY", "Cartago");

    // Un intervalo inválido (texto, cero o negativo) vuelve al valor por defecto
    config.interval_seconds = atoi(getenv_or_default("CHECK_INTERVAL_SECONDS", "30"));
    if (config.interval_seconds <= 0) {
        config.interval_seconds = DEFAULT_INTERVAL_SECONDS;
    }

    return config;
}

// Convierte la fila 'row' del resultado de fetch_targets() en un target_t.
// Los punteros apuntan dentro de 'res', así que solo son válidos hasta PQclear.
static target_t parse_target(const PGresult* res, int row) {
    target_t target;

    target.target_id     = PQgetvalue(res, row, COL_TARGET_ID);
    target.dns_record_id = PQgetvalue(res, row, COL_DNS_RECORD_ID);
    target.retries       = atoi(PQgetvalue(res, row, COL_RETRIES));

    target.check.host            = PQgetvalue(res, row, COL_IP_ADDRESS);
    target.check.port            = atoi(PQgetvalue(res, row, COL_PORT));
    target.check.check_type      = PQgetvalue(res, row, COL_CHECK_TYPE);
    target.check.timeout_ms      = atoi(PQgetvalue(res, row, COL_TIMEOUT_MS));
    target.check.http_path       = PQgetvalue(res, row, COL_HTTP_PATH);
    target.check.expected_codes  = PQgetvalue(res, row, COL_EXPECTED_CODES);
    target.check.basic_auth_user = PQgetvalue(res, row, COL_BASIC_AUTH_USER);
    target.check.basic_auth_pass = PQgetvalue(res, row, COL_BASIC_AUTH_PASS);

    // Siempre se hace al menos un intento
    if (target.retries <= 0) {
        target.retries = 1;
    }

    return target;
}

// Ejecuta el check 'retries' veces y decide el estado por mayoría simple.
// Devuelve 1 si el target está sano y deja en 'avg_latency' el promedio.
static int evaluate_target(const target_t* target, double* avg_latency) {
    const check_params_t* check = &target->check;
    int successes = 0;
    double total_latency = 0.0;

    for (int attempt = 1; attempt <= target->retries; attempt++) {
        double latency = 0.0;
        int alive = run_check(check, &latency);

        successes += alive;       // run_check devuelve 1 (UP) o 0 (DOWN)
        total_latency += latency; // Se promedian todos los intentos, no solo los exitosos

        printf("[HEALTH_CHECKER] [%s] intento %d/%d target=%s:%d -> %s (%.2fms)\n",
               check->check_type,
               attempt,
               target->retries,
               check->host,
               check->port,
               alive ? "UP" : "DOWN",
               latency);
    }

    *avg_latency = total_latency / target->retries;

    // Mayoría simple: más de la mitad de los intentos deben ser exitosos
    int healthy = successes > (target->retries / 2);

    printf("[HEALTH_CHECKER] Resultado final target=%s:%d successes=%d/%d -> %s (avg %.2fms)\n",
           check->host,
           check->port,
           successes,
           target->retries,
           healthy ? "HEALTHY" : "UNHEALTHY",
           *avg_latency);

    return healthy;
}

// Verifica un target y persiste el resultado: primero el histórico en
// health_results y luego el estado vigente en dns_records.
static void process_target(PGconn* conn, const target_t* target, const checker_location_t* location) {
    double avg_latency = 0.0;
    int healthy = evaluate_target(target, &avg_latency);

    save_health_result(conn, target->target_id, healthy, avg_latency, location);
    update_dns_record_health(conn, target->dns_record_id, healthy);
}

// Un ciclo completo: validar conexión, leer targets y procesar cada uno.
// Cualquier fallo aborta solo este ciclo; el siguiente vuelve a intentarlo.
static void run_cycle(PGconn* conn, const checker_config_t* config) {
    if (!ensure_db_connection(conn)) {
        return;
    }

    PGresult* targets = fetch_targets(conn);
    if (targets == NULL) {
        return;
    }

    int count = PQntuples(targets);
    for (int row = 0; row < count; row++) {
        target_t target = parse_target(targets, row);
        process_target(conn, &target, &config->location);
    }

    // Libera el resultado; los punteros de cada target_t dejan de ser válidos aquí
    PQclear(targets);
}

int main(void) {
    // 1. Configuración local
    checker_config_t config = load_config();

    printf("Health Checker iniciado (Proyecto1_IC7602)...\n");
    printf("[HEALTH_CHECKER] location=%s country=%s city=%s lat=%s lon=%s\n",
           config.location.location_id,
           config.location.country,
           config.location.city,
           config.location.latitude,
           config.location.longitude);

    // 2. Inicialización global de libcurl (una sola vez por proceso)
    curl_global_init(CURL_GLOBAL_ALL);

    // 3. Conexión inicial a la base de datos; sin ella no se puede arrancar
    PGconn *conn = connect_to_db();
    if (conn == NULL) {
        curl_global_cleanup();
        return 1;
    }

    // 4. Bucle principal: un ciclo y luego la pausa, pase lo que pase en el ciclo
    while (1) {
        run_cycle(conn, &config);
        sleep(config.interval_seconds);
    }

    // No se alcanza en ejecución normal (el proceso termina por señal)
    PQfinish(conn);
    curl_global_cleanup();
    return 0;
}
