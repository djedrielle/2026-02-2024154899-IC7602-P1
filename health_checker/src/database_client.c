#include "database_client.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Consulta de targets. Los COALESCE garantizan que ninguna columna llegue
// como NULL, así el código en C no necesita verificar PQgetisnull.
static const char* SELECT_TARGETS_QUERY =
    "SELECT "
    "id::text, "
    "COALESCE(dns_record_id::text, ''), "
    "ip_address, "
    "port::text, "
    "check_type, "
    "timeout_ms::text, "
    "COALESCE(retries, 3)::text, "
    "COALESCE(http_path, '/'), "
    "COALESCE(expected_status_codes::text, '{200}'), "
    "COALESCE(basic_auth_user, ''), "
    "COALESCE(basic_auth_pass, '') "
    "FROM targets";

static const char* INSERT_RESULT_QUERY =
    "INSERT INTO health_results "
    "(target_id, is_healthy, latency_ms, checker_location_id, "
    "checker_latitude, checker_longitude, checker_country, checker_city) "
    "VALUES ($1, $2, $3, $4, $5, $6, $7, $8)";

static const char* UPDATE_DNS_HEALTH_QUERY =
    "UPDATE dns_records "
    "SET healthy = $1 "
    "WHERE id = $2";

// Literal booleano que PostgreSQL entiende al recibir parámetros en texto.
// Al ser cadenas constantes no hace falta un buffer por llamada.
static const char* pg_bool(int value) {
    return value ? "true" : "false";
}

// Ejecuta un INSERT/UPDATE parametrizado (parámetros en formato texto).
// Centraliza el manejo de errores y libera el PGresult siempre.
// Devuelve 1 si el comando se ejecutó correctamente.
static int exec_command(
    PGconn *conn,
    const char* query,
    int param_count,
    const char* const* values,
    const char* error_prefix
) {
    PGresult *res = PQexecParams(
        conn,
        query,
        param_count,
        NULL,   // Tipos de parámetro: PostgreSQL los infiere
        values,
        NULL,   // Longitudes: no se necesitan para parámetros en texto
        NULL,   // Formatos: todos en texto
        0       // Resultado en formato texto
    );

    int ok = (PQresultStatus(res) == PGRES_COMMAND_OK);
    if (!ok) {
        fprintf(stderr, "%s: %s\n", error_prefix, PQerrorMessage(conn));
    }

    PQclear(res);
    return ok;
}

PGconn* connect_to_db(void) {
    // La cadena de conexión viene del .env / entorno del contenedor
    const char* database_url = getenv("DATABASE_URL");
    if (database_url == NULL || *database_url == '\0') {
        fprintf(stderr, "Error: DATABASE_URL no está definida\n");
        return NULL;
    }

    PGconn *conn = PQconnectdb(database_url);

    if (PQstatus(conn) == CONNECTION_OK) {
        printf("[HEALTH_CHECKER] Conectado a la base de datos\n");
        return conn;
    }

    // PQconnectdb siempre reserva memoria, incluso si falla: hay que liberarla
    fprintf(stderr, "Error conectando a la base de datos: %s\n", PQerrorMessage(conn));
    PQfinish(conn);
    return NULL;
}

int ensure_db_connection(PGconn *conn) {
    // Caso común: la conexión sigue viva, no hay nada que hacer
    if (PQstatus(conn) == CONNECTION_OK) {
        return 1;
    }

    fprintf(stderr, "[HEALTH_CHECKER] Conexión perdida, intentando reconectar...\n");
    PQreset(conn); // Reintenta con los mismos parámetros de la conexión original

    if (PQstatus(conn) == CONNECTION_OK) {
        printf("[HEALTH_CHECKER] Reconectado a la base de datos\n");
        return 1;
    }

    fprintf(stderr, "Error reconectando: %s\n", PQerrorMessage(conn));
    return 0;
}

PGresult* fetch_targets(PGconn *conn) {
    PGresult *res = PQexec(conn, SELECT_TARGETS_QUERY);

    if (PQresultStatus(res) != PGRES_TUPLES_OK) {
        fprintf(stderr, "Error en SELECT: %s\n", PQerrorMessage(conn));
        PQclear(res);
        return NULL; // El llamador solo debe distinguir éxito (no NULL) o fallo
    }

    return res;
}

void save_health_result(
    PGconn *conn,
    const char* target_id,
    int is_healthy,
    double latency,
    const checker_location_t* location
) {
    // La latencia se envía como texto con 4 decimales de precisión
    char latency_value[64];
    snprintf(latency_value, sizeof(latency_value), "%.4f", latency);

    // El orden debe coincidir con $1..$8 de INSERT_RESULT_QUERY
    const char* values[8] = {
        target_id,
        pg_bool(is_healthy),
        latency_value,
        location->location_id,
        location->latitude,
        location->longitude,
        location->country,
        location->city
    };

    exec_command(conn, INSERT_RESULT_QUERY, 8, values, "Error guardando resultado");
}

void update_dns_record_health(PGconn *conn, const char* dns_record_id, int is_healthy) {
    // Targets sin registro DNS asociado no tienen nada que actualizar
    if (dns_record_id == NULL || *dns_record_id == '\0') {
        return;
    }

    // $1 = healthy, $2 = id del registro
    const char* values[2] = {
        pg_bool(is_healthy),
        dns_record_id
    };

    if (exec_command(conn, UPDATE_DNS_HEALTH_QUERY, 2, values,
                     "Error actualizando dns_records.healthy")) {
        printf("[HEALTH_CHECKER] dns_record_id=%s actualizado a healthy=%s\n",
               dns_record_id,
               pg_bool(is_healthy));
    }
}
