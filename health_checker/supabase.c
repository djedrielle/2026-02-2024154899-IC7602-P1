#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <curl/curl.h>
#include "supabase.h"

/* Buffer dinámico para acumular la respuesta HTTP */
struct membuf { char *data; size_t len; };

static size_t write_cb(void *ptr, size_t size, size_t nmemb, void *userdata) {
    struct membuf *mb = (struct membuf *)userdata;
    size_t add = size * nmemb;
    char *n = realloc(mb->data, mb->len + add + 1);
    if (!n) return 0; /* aborta la transferencia si no hay memoria */
    mb->data = n;
    memcpy(mb->data + mb->len, ptr, add);
    mb->len += add;
    mb->data[mb->len] = '\0';
    return add;
}

void supabase_init(supabase_client_t *sb, const config_t *cfg) {
    strncpy(sb->base_url, cfg->supabase_url, sizeof(sb->base_url) - 1);
    strncpy(sb->api_key, cfg->supabase_key, sizeof(sb->api_key) - 1);
}

/* Construye los headers comunes que exige PostgREST (apikey + bearer). */
static struct curl_slist *common_headers(const supabase_client_t *sb, int with_json_body) {
    struct curl_slist *h = NULL;
    char auth[600];
    snprintf(auth, sizeof(auth), "Authorization: Bearer %s", sb->api_key);
    h = curl_slist_append(h, auth);

    char apikey[550];
    snprintf(apikey, sizeof(apikey), "apikey: %s", sb->api_key);
    h = curl_slist_append(h, apikey);

    if (with_json_body) {
        h = curl_slist_append(h, "Content-Type: application/json");
        h = curl_slist_append(h, "Prefer: return=minimal,resolution=merge-duplicates");
    }
    return h;
}

char *supabase_get(supabase_client_t *sb, const char *path_and_query) {
    CURL *curl = curl_easy_init();
    if (!curl) return NULL;

    char url[1024];
    snprintf(url, sizeof(url), "%s/rest/v1/%s", sb->base_url, path_and_query);

    struct membuf mb = { .data = malloc(1), .len = 0 };
    mb.data[0] = '\0';

    struct curl_slist *headers = common_headers(sb, 0);
    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_cb);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &mb);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 10L);

    CURLcode rc = curl_easy_perform(curl);
    long status = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &status);

    curl_slist_free_all(headers);
    curl_easy_cleanup(curl);

    if (rc != CURLE_OK || status >= 300) {
        fprintf(stderr, "[supabase] GET %s -> rc=%d http=%ld\n", path_and_query, rc, status);
        free(mb.data);
        return NULL;
    }
    return mb.data; /* el caller debe hacer free() */
}

static int do_write(supabase_client_t *sb, const char *path_and_query,
                     const char *json_body, const char *method) {
    CURL *curl = curl_easy_init();
    if (!curl) return -1;

    char url[1024];
    snprintf(url, sizeof(url), "%s/rest/v1/%s", sb->base_url, path_and_query);

    struct membuf mb = { .data = malloc(1), .len = 0 };
    mb.data[0] = '\0';

    struct curl_slist *headers = common_headers(sb, 1);
    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, method);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, json_body);
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_cb);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &mb);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 10L);

    CURLcode rc = curl_easy_perform(curl);
    long status = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &status);

    curl_slist_free_all(headers);
    curl_easy_cleanup(curl);
    free(mb.data);

    if (rc != CURLE_OK || status >= 300) {
        fprintf(stderr, "[supabase] %s %s -> rc=%d http=%ld\n", method, path_and_query, rc, status);
        return -1;
    }
    return 0;
}

int supabase_post(supabase_client_t *sb, const char *path, const char *json_body) {
    return do_write(sb, path, json_body, "POST");
}

int supabase_patch(supabase_client_t *sb, const char *path_and_query, const char *json_body) {
    return do_write(sb, path_and_query, json_body, "PATCH");
}