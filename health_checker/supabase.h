/*
 * supabase.h — Cliente mínimo para la REST API de Supabase (PostgREST).
 *
 * Se usa para:
 *   - Leer los registros DNS y su configuración de health check.
 *   - Insertar el resultado de cada prueba (tabla health_checks).
 *   - Actualizar el estado agregado (healthy/unhealthy) de un registro
 *     y, en el caso "round-trip", el rtt medido por este checker.
 */
#ifndef SUPABASE_H
#define SUPABASE_H

#include "config.h"

typedef struct {
    char base_url[256];   /* config->supabase_url */
    char api_key[512];    /* config->supabase_key */
} supabase_client_t;

void supabase_init(supabase_client_t *sb, const config_t *cfg);

/* GET {base_url}/rest/v1/{path_and_query} -> string JSON (malloc'd, caller frees).
 * Devuelve NULL en error de red/HTTP. */
char *supabase_get(supabase_client_t *sb, const char *path_and_query);

/* POST body JSON a {base_url}/rest/v1/{path} (insert). Devuelve 0 en éxito. */
int supabase_post(supabase_client_t *sb, const char *path, const char *json_body);

/* PATCH body JSON a {base_url}/rest/v1/{path_and_query} (update). Devuelve 0 en éxito. */
int supabase_patch(supabase_client_t *sb, const char *path_and_query, const char *json_body);

#endif