#include "database_client.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Consulta de targets. Los COALESCE garantizan que ninguna columna llegue
// como NULL, así el código en C no necesita verificar PQgetisnull.
// El orden de las columnas debe coincidir con enum target_column.
static const char* SELECT_TARGETS_QUERY =
    "SELECT "
    "id::text, "
    "record_name, "
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
    "(target_id, record_name, ip_address, is_healthy, latency_ms, checker_location_id, "
    "checker_latitude, checker_longitude, checker_country, checker_city) "
    "VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)";

// Reconstruye records.ips cambiando solo el "healthy" de la IP indicada.
//   $1 = healthy (boolean), $2 = nombre del registro, $3 = IP
// - jsonb_array_elements ... WITH ORDINALITY recorre el arreglo recordando la
//   posición de cada elemento, y ORDER BY pos lo rearma en el mismo orden
//   (importante para el round-robin de los registros "multi").
// - jsonb_set solo modifica el elemento cuya "ip" coincide; el resto de sus
//   campos (weight, country_code, ...) se conserva.
// - El filtro con @> exige que la IP exista en el registro: si no existe, el
//   UPDATE afecta 0 filas y se reporta en el log en lugar de fallar en silencio.
static const char* UPDATE_IP_HEALTH_QUERY =
    "UPDATE records "
    "SET ips = ("
        "SELECT jsonb_agg("
            "CASE WHEN elem->>'ip' = $3::text "
                 "THEN jsonb_set(elem, '{healthy}', to_jsonb($1::boolean)) "
                 "ELSE elem "
            "END ORDER BY pos) "
        "FROM jsonb_array_elements(ips) WITH ORDINALITY AS t(elem, pos)"
    ") "
    "WHERE name = $2 "
    "AND ips @> jsonb_build_array(jsonb_build_object('ip', $3::text))";

// Literal booleano que PostgreSQL entiende al recibir parámetros en texto.
// Al ser cadenas constantes no hace falta un buffer por llamada.
static const char* pg_bool(int value) {
    return value ? "true" : "false";
}

// Ejecuta un INSERT/UPDATE parametrizado (parámetros en formato texto).
// Centraliza el manejo de errores y libera el PGresult siempre.
// Devuelve la cantidad de filas afectadas, o -1 si el comando falló.
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

    int affected_rows = -1;

    if (PQresultStatus(res) == PGRES_COMMAND_OK) {
        // PQcmdTuples devuelve el conteo como texto (p. ej. "1" o "0")
        affected_rows = atoi(PQcmdTuples(res));
    } else {
        fprintf(stderr, "%s: %s\n", error_prefix, PQerrorMessage(conn));
    }

    PQclear(res);
    return affected_rows;
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
    const char* record_name,
    const char* ip_address,
    int is_healthy,
    double latency,
    const checker_location_t* location
) {
    // La latencia se envía como texto con 4 decimales de precisión
    char latency_value[64];
    snprintf(latency_value, sizeof(latency_value), "%.4f", latency);

    // El orden debe coincidir con $1..$10 de INSERT_RESULT_QUERY
    const char* values[10] = {
        target_id,
        record_name,
        ip_address,
        pg_bool(is_healthy),
        latency_value,
        location->location_id,
        location->latitude,
        location->longitude,
        location->country,
        location->city
    };

    exec_command(conn, INSERT_RESULT_QUERY, 10, values, "Error guardando resultado");
}

void update_ip_health(PGconn *conn, const char* record_name, const char* ip_address, int is_healthy) {
    // El orden debe coincidir con $1..$3 de UPDATE_IP_HEALTH_QUERY
    const char* values[3] = {
        pg_bool(is_healthy),
        record_name,
        ip_address
    };

    int updated = exec_command(conn, UPDATE_IP_HEALTH_QUERY, 3, values,
                               "Error actualizando records.ips");

    if (updated > 0) {
        printf("[HEALTH_CHECKER] record=%s ip=%s actualizado a healthy=%s\n",
               record_name,
               ip_address,
               pg_bool(is_healthy));
    } else if (updated == 0) {
        // El target apunta a un registro o IP que no existe en records:
        // normalmente la IP se editó en la UI sin actualizar su target
        fprintf(stderr, "[HEALTH_CHECKER] Aviso: record=%s no contiene la ip=%s en records.ips\n",
                record_name,
                ip_address);
    }
    // updated == -1: exec_command ya imprimió el error
}
