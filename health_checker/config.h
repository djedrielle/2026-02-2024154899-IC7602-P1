/*
 * config.h — Carga de configuración desde variables de entorno.
 *
 * Ninguna URL, credencial o parámetro va quemado en el código: todo se
 * inyecta vía variables de entorno (Docker / Helm / Compose).
 */
#ifndef CONFIG_H
#define CONFIG_H

typedef struct {
    /* Conexión a Supabase (REST + API key) */
    char supabase_url[256];     /* ej: https://xxxx.supabase.co        */
    char supabase_key[512];     /* service_role o anon key             */

    /* Identidad y ubicación de ESTE Health Checker (para round-trip)   */
    char checker_id[64];        /* id único de esta instancia          */
    double lat;
    double lon;
    char country[64];
    char city[64];

    /* Comportamiento del scheduler */
    int  poll_interval_sec;     /* cada cuánto se releen los registros */
    int  worker_threads;        /* checks concurrentes máximos         */
} config_t;

/* Llena cfg desde el entorno. Termina el proceso si falta algo crítico. */
void config_load(config_t *cfg);

#endif