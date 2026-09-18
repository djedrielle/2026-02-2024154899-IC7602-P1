/*
 * main.c — Health Checker (Proyecto 1, IC7602)
 *
 * Flujo:
 *   1. Se registra a sí mismo en la tabla `health_checkers` (id + ubicación).
 *   2. Cada POLL_INTERVAL_SECONDS lee todos los targets a monitorear
 *      (tabla `dns_targets`) desde Supabase.
 *   3. Por cada target, en un hilo (acotado por un semáforo a
 *      WORKER_THREADS concurrentes), ejecuta el check TCP o HTTP que
 *      corresponda, según la configuración del propio registro.
 *   4. Inserta el resultado individual en `health_check_results`
 *      (incluye rtt_ms y la ubicación de este checker: esto es lo que
 *      el DNS API usa después para resolver registros "round-trip").
 *   5. Recalcula el estado agregado del target por MAYORÍA SIMPLE entre
 *      los resultados más recientes de cada checker activo, y hace un
 *      PATCH de `dns_targets.status`.
 *
 * Esquema de datos asumido en Supabase (ajustar nombres si el equipo
 * definió otros en el DNS UI / DNS API):
 *
 *   dns_targets(
 *     id text primary key, record_id text, ip text,
 *     hc_type text,                 -- 'tcp' | 'http'
 *     hc_host text, hc_port int,
 *     hc_path text,                 -- sólo http
 *     hc_expected_codes jsonb,      -- sólo http, ej. [200,204]
 *     hc_timeout_ms int, hc_retries int, hc_interval_sec int,
 *     hc_basic_user text, hc_basic_pass text,
 *     status text                   -- 'healthy' | 'unhealthy' (salida)
 *   )
 *
 *   health_checkers(
 *     id text primary key, lat float8, lon float8,
 *     country text, city text, last_seen timestamptz
 *   )
 *
 *   health_check_results(
 *     id bigserial primary key, target_id text, checker_id text,
 *     healthy bool, rtt_ms float8,
 *     checker_lat float8, checker_lon float8,
 *     checker_country text, checker_city text,
 *     checked_at timestamptz
 *   )
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <pthread.h>
#include <semaphore.h>
#include <curl/curl.h>

#include "config.h"
#include "supabase.h"
#include "checks.h"
#include "cJSON.h"

static config_t          g_cfg;
static supabase_client_t g_sb;
static sem_t             g_pool_sem; /* limita hilos concurrentes */

/* ---------- utilidades ---------- */

static void iso8601_now(char *buf, size_t sz) {
    time_t t = time(NULL);
    struct tm tm_utc;
    gmtime_r(&t, &tm_utc);
    strftime(buf, sz, "%Y-%m-%dT%H:%M:%SZ", &tm_utc);
}

/* Registra/actualiza este checker (upsert por id) al iniciar. */
static void register_self(void) {
    char body[512], ts[64];
    iso8601_now(ts, sizeof(ts));
    snprintf(body, sizeof(body),
        "{\"id\":\"%s\",\"lat\":%.6f,\"lon\":%.6f,"
        "\"country\":\"%s\",\"city\":\"%s\",\"last_seen\":\"%s\"}",
        g_cfg.checker_id, g_cfg.lat, g_cfg.lon, g_cfg.country, g_cfg.city, ts);

    if (supabase_post(&g_sb, "health_checkers", body) != 0)
        fprintf(stderr, "[main] WARN: no se pudo registrar el checker (¿ya existe? ok)\n");
    else
        fprintf(stdout, "[main] checker '%s' registrado\n", g_cfg.checker_id);
}

/* ---------- estructura de un target y parseo desde JSON ---------- */

typedef struct {
    char id[64];
    char hc_type[8];        /* "tcp" | "http" */
    char hc_host[256];
    int  hc_port;
    char hc_path[256];
    int  expected_codes[16];
    int  n_expected_codes;
    int  timeout_ms;
    int  retries;
    char basic_user[128];
    char basic_pass[128];
    int  has_basic_auth;
} target_t;

static void parse_target(cJSON *item, target_t *t) {
    memset(t, 0, sizeof(*t));
    cJSON *j;

    if ((j = cJSON_GetObjectItem(item, "id")) && cJSON_IsString(j))
        strncpy(t->id, j->valuestring, sizeof(t->id) - 1);
    if ((j = cJSON_GetObjectItem(item, "hc_type")) && cJSON_IsString(j))
        strncpy(t->hc_type, j->valuestring, sizeof(t->hc_type) - 1);
    if ((j = cJSON_GetObjectItem(item, "hc_host")) && cJSON_IsString(j))
        strncpy(t->hc_host, j->valuestring, sizeof(t->hc_host) - 1);
    if ((j = cJSON_GetObjectItem(item, "hc_port")) && cJSON_IsNumber(j))
        t->hc_port = j->valueint;
    if ((j = cJSON_GetObjectItem(item, "hc_path")) && cJSON_IsString(j))
        strncpy(t->hc_path, j->valuestring, sizeof(t->hc_path) - 1);
    if ((j = cJSON_GetObjectItem(item, "hc_timeout_ms")) && cJSON_IsNumber(j))
        t->timeout_ms = j->valueint;
    else
        t->timeout_ms = 2000;
    if ((j = cJSON_GetObjectItem(item, "hc_retries")) && cJSON_IsNumber(j))
        t->retries = j->valueint;
    else
        t->retries = 2;

    if ((j = cJSON_GetObjectItem(item, "hc_expected_codes")) && cJSON_IsArray(j)) {
        int n = cJSON_GetArraySize(j);
        for (int i = 0; i < n && i < 16; i++)
            t->expected_codes[t->n_expected_codes++] = cJSON_GetArrayItem(j, i)->valueint;
    }
    if (t->n_expected_codes == 0) { t->expected_codes[0] = 200; t->n_expected_codes = 1; }

    cJSON *bu = cJSON_GetObjectItem(item, "hc_basic_user");
    cJSON *bp = cJSON_GetObjectItem(item, "hc_basic_pass");
    if (bu && cJSON_IsString(bu) && bp && cJSON_IsString(bp) && strlen(bu->valuestring) > 0) {
        strncpy(t->basic_user, bu->valuestring, sizeof(t->basic_user) - 1);
        strncpy(t->basic_pass, bp->valuestring, sizeof(t->basic_pass) - 1);
        t->has_basic_auth = 1;
    }
}

/* ---------- mayoría simple ---------- */

/* Consulta los resultados más recientes de cada checker para `target_id`
 * y decide healthy/unhealthy por mayoría simple entre checkers distintos. */
static int majority_is_healthy(const char *target_id) {
    char q[256];
    snprintf(q, sizeof(q),
        "health_check_results?target_id=eq.%s&order=checked_at.desc&limit=50",
        target_id);

    char *json = supabase_get(&g_sb, q);
    if (!json) return -1; /* sin datos: el llamador decide qué hacer */

    cJSON *arr = cJSON_Parse(json);
    free(json);
    if (!arr || !cJSON_IsArray(arr)) { if (arr) cJSON_Delete(arr); return -1; }

    /* nos quedamos sólo con el resultado más reciente por checker_id */
    char seen[32][64]; int n_seen = 0;
    int healthy_votes = 0, total_votes = 0;

    cJSON *item;
    cJSON_ArrayForEach(item, arr) {
        cJSON *cid = cJSON_GetObjectItem(item, "checker_id");
        cJSON *hlt = cJSON_GetObjectItem(item, "healthy");
        if (!cid || !cJSON_IsString(cid) || !hlt) continue;

        int already = 0;
        for (int i = 0; i < n_seen; i++)
            if (strcmp(seen[i], cid->valuestring) == 0) { already = 1; break; }
        if (already || n_seen >= 32) continue;

        strncpy(seen[n_seen++], cid->valuestring, 63);
        total_votes++;
        if (cJSON_IsTrue(hlt)) healthy_votes++;
    }
    cJSON_Delete(arr);

    if (total_votes == 0) return -1;
    return (healthy_votes * 2 > total_votes) ? 1 : 0; /* mayoría simple */
}

/* ---------- ejecución de un check individual (una thread por target) ---------- */

typedef struct { target_t t; } worker_arg_t;

static void *check_worker(void *arg) {
    worker_arg_t *wa = (worker_arg_t *)arg;
    target_t *t = &wa->t;

    check_result_t res;
    if (strcmp(t->hc_type, "tcp") == 0) {
        res = tcp_health_check(t->hc_host, t->hc_port, t->timeout_ms, t->retries);
    } else { /* "http" */
        char base_url[300];
        snprintf(base_url, sizeof(base_url), "http://%s:%d", t->hc_host, t->hc_port);
        res = http_health_check(base_url, t->hc_path, t->timeout_ms, t->retries,
                                 t->expected_codes, t->n_expected_codes,
                                 t->has_basic_auth ? t->basic_user : NULL,
                                 t->has_basic_auth ? t->basic_pass : NULL);
    }

    /* 1) log del resultado individual (alimenta el algoritmo round-trip) */
    char ts[64]; iso8601_now(ts, sizeof(ts));
    char body[700];

    /* rtt_ms puede ser null si el check nunca respondió */
    char rtt_field[32];
    if (res.rtt_ms >= 0) snprintf(rtt_field, sizeof(rtt_field), "%.2f", res.rtt_ms);
    else strcpy(rtt_field, "null");

    snprintf(body, sizeof(body),
        "{\"target_id\":\"%s\",\"checker_id\":\"%s\",\"healthy\":%s,"
        "\"rtt_ms\":%s,\"checker_lat\":%.6f,\"checker_lon\":%.6f,"
        "\"checker_country\":\"%s\",\"checker_city\":\"%s\",\"checked_at\":\"%s\"}",
        t->id, g_cfg.checker_id, res.healthy ? "true" : "false",
        rtt_field, g_cfg.lat, g_cfg.lon, g_cfg.country, g_cfg.city, ts);

    supabase_post(&g_sb, "health_check_results", body);

    /* 2) recalcular estado agregado por mayoría simple y actualizarlo */
    int majority = majority_is_healthy(t->id);
    if (majority >= 0) {
        char patch_q[128], patch_body[64];
        snprintf(patch_q, sizeof(patch_q), "dns_targets?id=eq.%s", t->id);
        snprintf(patch_body, sizeof(patch_body),
                 "{\"status\":\"%s\"}", majority ? "healthy" : "unhealthy");
        supabase_patch(&g_sb, patch_q, patch_body);
    }

    fprintf(stdout, "[check] target=%s type=%s healthy=%d rtt=%.1fms\n",
            t->id, t->hc_type, res.healthy, res.rtt_ms);

    free(wa);
    sem_post(&g_pool_sem); /* libera un cupo del pool de hilos */
    return NULL;
}

static void dispatch_check(target_t *t) {
    sem_wait(&g_pool_sem); /* espera cupo si ya hay WORKER_THREADS activos */

    worker_arg_t *wa = malloc(sizeof(worker_arg_t));
    wa->t = *t;

    pthread_t th;
    if (pthread_create(&th, NULL, check_worker, wa) != 0) {
        fprintf(stderr, "[main] ERROR creando thread para target %s\n", t->id);
        free(wa);
        sem_post(&g_pool_sem);
        return;
    }
    pthread_detach(th);
}

/* ---------- ciclo principal ---------- */

static void poll_and_check_all(void) {
    char *json = supabase_get(&g_sb,
        "dns_targets?select=id,hc_type,hc_host,hc_port,hc_path,hc_expected_codes,"
        "hc_timeout_ms,hc_retries,hc_basic_user,hc_basic_pass");
    if (!json) {
        fprintf(stderr, "[main] no se pudieron leer los targets, se reintenta en el próximo ciclo\n");
        return;
    }

    cJSON *arr = cJSON_Parse(json);
    free(json);
    if (!arr || !cJSON_IsArray(arr)) {
        fprintf(stderr, "[main] respuesta de dns_targets no es un array JSON válido\n");
        if (arr) cJSON_Delete(arr);
        return;
    }

    int total = cJSON_GetArraySize(arr);
    fprintf(stdout, "[main] %d targets a verificar\n", total);

    cJSON *item;
    cJSON_ArrayForEach(item, arr) {
        target_t t;
        parse_target(item, &t);
        if (t.id[0] == '\0' || t.hc_type[0] == '\0') continue; /* registro sin health check configurado */
        dispatch_check(&t);
    }
    cJSON_Delete(arr);
}

int main(void) {
    setvbuf(stdout, NULL, _IOLBF, 0); /* logs en línea, útil para `docker logs` */
    curl_global_init(CURL_GLOBAL_DEFAULT);
    config_load(&g_cfg);
    supabase_init(&g_sb, &g_cfg);
    sem_init(&g_pool_sem, 0, g_cfg.worker_threads);

    register_self();

    fprintf(stdout, "[main] Health Checker iniciado. Ciclo cada %ds.\n", g_cfg.poll_interval_sec);
    for (;;) {
        poll_and_check_all();
        sleep(g_cfg.poll_interval_sec);
    }

    curl_global_cleanup(); /* inalcanzable, documenta la limpieza esperada */
    return 0;
}