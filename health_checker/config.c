#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "config.h"

/* Copia una env var a dst; si no existe y es requerida, aborta. */
static void req_str(const char *name, char *dst, size_t sz) {
    const char *v = getenv(name);
    if (!v || !*v) {
        fprintf(stderr, "[config] ERROR: falta variable de entorno %s\n", name);
        exit(EXIT_FAILURE);
    }
    strncpy(dst, v, sz - 1);
    dst[sz - 1] = '\0';
}

static void opt_str(const char *name, char *dst, size_t sz, const char *def) {
    const char *v = getenv(name);
    strncpy(dst, (v && *v) ? v : def, sz - 1);
    dst[sz - 1] = '\0';
}

static double opt_double(const char *name, double def) {
    const char *v = getenv(name);
    return (v && *v) ? atof(v) : def;
}

static int opt_int(const char *name, int def) {
    const char *v = getenv(name);
    return (v && *v) ? atoi(v) : def;
}

void config_load(config_t *cfg) {
    memset(cfg, 0, sizeof(*cfg));

    req_str("SUPABASE_URL", cfg->supabase_url, sizeof(cfg->supabase_url));
    req_str("SUPABASE_KEY", cfg->supabase_key, sizeof(cfg->supabase_key));

    opt_str("CHECKER_ID", cfg->checker_id, sizeof(cfg->checker_id), "checker-1");
    opt_str("CHECKER_COUNTRY", cfg->country, sizeof(cfg->country), "CR");
    opt_str("CHECKER_CITY", cfg->city, sizeof(cfg->city), "Cartago");
    cfg->lat = opt_double("CHECKER_LAT", 9.8489);
    cfg->lon = opt_double("CHECKER_LON", -83.9091);

    cfg->poll_interval_sec = opt_int("POLL_INTERVAL_SECONDS", 30);
    cfg->worker_threads    = opt_int("WORKER_THREADS", 8);

    fprintf(stdout,
        "[config] checker_id=%s loc=(%.4f,%.4f) %s/%s interval=%ds threads=%d\n",
        cfg->checker_id, cfg->lat, cfg->lon, cfg->city, cfg->country,
        cfg->poll_interval_sec, cfg->worker_threads);
}