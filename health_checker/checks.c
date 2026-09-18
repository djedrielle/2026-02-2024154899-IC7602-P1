#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <netdb.h>
#include <curl/curl.h>
#include "checks.h"

static double now_ms(void) {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (double)tv.tv_sec * 1000.0 + (double)tv.tv_usec / 1000.0;
}

/* Un único intento de conexión TCP con timeout (connect no bloqueante + select). */
static bool tcp_attempt(const char *host, int port, int timeout_ms, double *rtt_out) {
    char portstr[8];
    snprintf(portstr, sizeof(portstr), "%d", port);

    struct addrinfo hints = {0}, *res = NULL;
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    if (getaddrinfo(host, portstr, &hints, &res) != 0 || !res) return false;

    int fd = socket(res->ai_family, res->ai_socktype, res->ai_protocol);
    if (fd < 0) { freeaddrinfo(res); return false; }

    fcntl(fd, F_SETFL, O_NONBLOCK);

    double t0 = now_ms();
    bool ok = false;
    int rc = connect(fd, res->ai_addr, res->ai_addrlen);
    if (rc == 0) {
        ok = true;
    } else if (errno == EINPROGRESS) {
        fd_set wfds; FD_ZERO(&wfds); FD_SET(fd, &wfds);
        struct timeval tv = { timeout_ms / 1000, (timeout_ms % 1000) * 1000 };
        if (select(fd + 1, NULL, &wfds, NULL, &tv) > 0) {
            int err = 0; socklen_t len = sizeof(err);
            getsockopt(fd, SOL_SOCKET, SO_ERROR, &err, &len);
            ok = (err == 0);
        }
    }
    if (ok) *rtt_out = now_ms() - t0;

    close(fd);
    freeaddrinfo(res);
    return ok;
}

check_result_t tcp_health_check(const char *host, int port, int timeout_ms, int retries) {
    check_result_t r = { .healthy = false, .rtt_ms = -1 };
    for (int i = 0; i <= retries; i++) {
        double rtt;
        if (tcp_attempt(host, port, timeout_ms, &rtt)) {
            r.healthy = true;
            r.rtt_ms = rtt;
            break;
        }
    }
    return r;
}

/* --- HTTP --- */
static size_t discard_cb(void *ptr, size_t size, size_t nmemb, void *userdata) {
    (void)ptr; (void)userdata;
    return size * nmemb; /* no nos interesa el body, sólo el código de estado */
}

static bool code_expected(long code, const int *expected_codes, int n) {
    for (int i = 0; i < n; i++) if (expected_codes[i] == (int)code) return true;
    return false;
}

static bool http_attempt(const char *url, const char *path, int timeout_ms,
                          const int *expected_codes, int n_codes,
                          const char *user, const char *pass, double *rtt_out) {
    CURL *curl = curl_easy_init();
    if (!curl) return false;

    char full_url[1024];
    snprintf(full_url, sizeof(full_url), "%s%s", url, path ? path : "");

    curl_easy_setopt(curl, CURLOPT_URL, full_url);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT_MS, (long)timeout_ms);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, discard_cb);
    curl_easy_setopt(curl, CURLOPT_NOBODY, 0L); /* GET normal */
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 0L);

    if (user && pass) {
        curl_easy_setopt(curl, CURLOPT_HTTPAUTH, (long)CURLAUTH_BASIC);
        curl_easy_setopt(curl, CURLOPT_USERNAME, user);
        curl_easy_setopt(curl, CURLOPT_PASSWORD, pass);
    }

    double t0 = now_ms();
    CURLcode rc = curl_easy_perform(curl);
    double rtt = now_ms() - t0;

    bool ok = false;
    if (rc == CURLE_OK) {
        long status = 0;
        curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &status);
        ok = code_expected(status, expected_codes, n_codes);
    }
    curl_easy_cleanup(curl);

    if (ok) *rtt_out = rtt;
    return ok;
}

check_result_t http_health_check(const char *url, const char *path,
                                  int timeout_ms, int retries,
                                  const int *expected_codes, int n_codes,
                                  const char *basic_user, const char *basic_pass) {
    check_result_t r = { .healthy = false, .rtt_ms = -1 };
    for (int i = 0; i <= retries; i++) {
        double rtt;
        if (http_attempt(url, path, timeout_ms, expected_codes, n_codes,
                          basic_user, basic_pass, &rtt)) {
            r.healthy = true;
            r.rtt_ms = rtt;
            break;
        }
    }
    return r;
}