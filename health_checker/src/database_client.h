#ifndef DATABASE_CLIENT_H
#define DATABASE_CLIENT_H

#include <libpq-fe.h>

/*
 * Identidad geográfica de esta instancia del Health Checker.
 * Se adjunta a cada resultado para saber desde dónde se hizo la verificación.
 */
typedef struct {
    const char* location_id;
    const char* latitude;
    const char* longitude;
    const char* country;
    const char* city;
} checker_location_t;

/*
 * Posición de cada columna en el resultado de fetch_targets().
 * Si se modifica el SELECT, este enum debe mantenerse en el mismo orden.
 */
enum target_column {
    COL_TARGET_ID = 0,
    COL_DNS_RECORD_ID,
    COL_IP_ADDRESS,
    COL_PORT,
    COL_CHECK_TYPE,
    COL_TIMEOUT_MS,
    COL_RETRIES,
    COL_HTTP_PATH,
    COL_EXPECTED_CODES,
    COL_BASIC_AUTH_USER,
    COL_BASIC_AUTH_PASS
};

/* Abre la conexión usando la variable de entorno DATABASE_URL. */
PGconn* connect_to_db(void);

/* Verifica la conexión y la restablece si se perdió. Devuelve 1 si está lista. */
int ensure_db_connection(PGconn *conn);

/* Obtiene todos los targets. Devuelve NULL en error; el llamador hace PQclear. */
PGresult* fetch_targets(PGconn *conn);

/* Inserta el resultado consolidado de un target en health_results. */
void save_health_result(
    PGconn *conn,
    const char* target_id,
    int is_healthy,
    double latency,
    const checker_location_t* location
);

/* Actualiza dns_records.healthy. No hace nada si dns_record_id está vacío. */
void update_dns_record_health(PGconn *conn, const char* dns_record_id, int is_healthy);

#endif
