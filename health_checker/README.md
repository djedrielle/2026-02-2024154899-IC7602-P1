# Health Checker — Proyecto 1 (IC 7602, Redes)

Implementación del componente **Health Checker** en GNU C, y de la lógica que
soporta el tipo de registro **`round-trip`**. Se asume que el **DNS
Interceptor** (Rust), el **DNS API** (Java) y el **DNS UI** (React) ya están
implementados por el resto del equipo, y que todos comparten la misma base de
datos Supabase.

## 1. Qué hace

Cada instancia de este programa:

1. Se registra a sí misma (id + ubicación geográfica simulada) en la tabla
   `health_checkers`.
2. Cada `POLL_INTERVAL_SECONDS` segundos, lee de Supabase todos los
   *targets* configurados con health check (tabla `dns_targets`).
3. Ejecuta, en paralelo (un hilo por target, acotado por `WORKER_THREADS`),
   el check correspondiente:
   - **TCP**: intenta el 3-way handshake con `host:port`.
   - **HTTP**: hace `GET host:port/path`, valida el código de respuesta
     contra `hc_expected_codes`, y soporta *basic auth* si el target lo
     requiere.
   Ambos respetan `timeout` y `retries` configurados por registro.
4. Inserta cada resultado individual (`healthy`, `rtt_ms`, y la ubicación de
   **este** checker) en `health_check_results`. Este historial es lo que
   alimenta la resolución de registros `round-trip` en el DNS API.
5. Recalcula el estado agregado del target por **mayoría simple**: toma el
   resultado más reciente de cada checker activo y, si más de la mitad lo
   reportan `healthy`, marca el target como `healthy` en `dns_targets`
   (y `unhealthy` en caso contrario). Con esto, si un registro queda
   `unhealthy`, el DNS Interceptor debe tratarlo como si no existiera
   (reenviar al DNS API / resolver público), tal como pide el enunciado.

## 2. Estrategia para el registro tipo `round-trip`

El enunciado exige elegir la IP con **menor latencia según la cercanía con
el cliente**. La implementación completa (mapear IP de origen → país/ciudad
vía la tabla *IP to Country*, y elegir el Health Checker más cercano) vive
en el **DNS API**, porque es quien conoce la IP de origen de la consulta.
Lo que este módulo aporta es el dato crudo necesario para esa decisión:

- Cada Health Checker mide su **propio** RTT hacia cada target y lo guarda
  en `health_check_results` junto con `checker_lat`, `checker_lon`,
  `checker_country`, `checker_city`.
- Con eso, el DNS API puede: (a) ubicar al cliente, (b) encontrar el
  Health Checker geográficamente más cercano al cliente, y (c) tomar el
  `rtt_ms` que **ese** checker reportó para cada IP candidata del registro,
  eligiendo la de menor latencia entre las que estén `healthy`.

Por eso el Datos Generales del proyecto invita a correr **varios**
Health Checkers en "ubicaciones" distintas (ver `docker-compose.snippet.yml`
con un ejemplo `checker-cr` / `checker-us`): con un solo checker no hay
forma de comparar cercanía.

## 3. Esquema de datos esperado en Supabase

Si el equipo ya definió otros nombres de tabla/columna en el DNS UI o el
DNS API, basta con ajustar las cadenas de consulta en `src/main.c`
(están todas centralizadas en `poll_and_check_all`, `check_worker` y
`majority_is_healthy`).

```sql
-- Targets a monitorear (uno por IP dentro de un registro multi/weight/geo/round-trip)
create table dns_targets (
  id                 text primary key,
  record_id          text not null,       -- FK lógica al registro DNS
  ip                 text not null,
  hc_type            text not null,       -- 'tcp' | 'http'
  hc_host            text not null,
  hc_port            int  not null,
  hc_path            text,                -- sólo http
  hc_expected_codes  jsonb,               -- sólo http, ej: [200, 204]
  hc_timeout_ms      int  default 2000,
  hc_retries         int  default 2,
  hc_interval_sec    int  default 30,     -- referencia para el DNS UI
  hc_basic_user      text,
  hc_basic_pass      text,
  status             text default 'unknown' -- salida: 'healthy' | 'unhealthy'
);

-- Health Checkers activos (para round-trip)
create table health_checkers (
  id         text primary key,
  lat        float8, lon float8,
  country    text, city text,
  last_seen  timestamptz
);

-- Historial de resultados individuales por checker (para mayoría y round-trip)
create table health_check_results (
  id              bigserial primary key,
  target_id       text not null,
  checker_id      text not null,
  healthy         boolean not null,
  rtt_ms          float8,             -- null si el intento nunca respondió
  checker_lat     float8, checker_lon float8,
  checker_country text, checker_city  text,
  checked_at      timestamptz not null
);
```

## 4. Variables de entorno

| Variable                 | Requerida | Descripción                                         | Default        |
|---------------------------|:---------:|------------------------------------------------------|----------------|
| `SUPABASE_URL`             | Sí        | URL base del proyecto Supabase                        | —              |
| `SUPABASE_KEY`             | Sí        | API key (service role recomendada)                    | —              |
| `CHECKER_ID`               | No        | Identificador único de esta instancia                 | `checker-1`    |
| `CHECKER_LAT` / `CHECKER_LON` | No     | Ubicación simulada de este checker                     | San José, CR   |
| `CHECKER_COUNTRY` / `CHECKER_CITY` | No | Ubicación simulada (texto)                        | `CR` / `Cartago` |
| `POLL_INTERVAL_SECONDS`    | No        | Cada cuánto se relee la lista de targets               | `30`           |
| `WORKER_THREADS`           | No        | Checks concurrentes máximos                            | `8`            |

Ningún valor queda quemado en el código: todo se inyecta por entorno, como
exige el enunciado.

## 5. Compilar y ejecutar

### Local (con `make`)

```bash
sudo apt-get install -y libcurl4-openssl-dev   # dependencia de build
make
SUPABASE_URL="https://xxxx.supabase.co" \
SUPABASE_KEY="xxxxx" \
CHECKER_ID="checker-cr" \
CHECKER_LAT="9.8489" CHECKER_LON="-83.9091" \
CHECKER_COUNTRY="CR" CHECKER_CITY="Cartago" \
./health-checker
```

### Docker

```bash
docker build -t health-checker ./health-checker
docker run --rm \
  -e SUPABASE_URL="https://xxxx.supabase.co" \
  -e SUPABASE_KEY="xxxxx" \
  -e CHECKER_ID="checker-cr" \
  -e CHECKER_LAT="9.8489" -e CHECKER_LON="-83.9091" \
  -e CHECKER_COUNTRY="CR" -e CHECKER_CITY="Cartago" \
  health-checker
```

### Docker Compose

Ver `docker-compose.snippet.yml`: incorporarlo al `docker-compose.yml`
general del proyecto (junto a DNS Interceptor, DNS API, DNS UI). Se
recomienda al menos **dos** instancias en ubicaciones distintas para que el
registro `round-trip` tenga datos que comparar.

## 6. Estructura del código

```
health-checker/
├── Dockerfile
├── Makefile
├── docker-compose.snippet.yml
├── README.md
└── src/
    ├── config.h / config.c    # lectura de variables de entorno
    ├── supabase.h / supabase.c# cliente REST mínimo para Supabase (GET/POST/PATCH)
    ├── checks.h / checks.c    # primitivas de check TCP y HTTP (con RTT)
    ├── cJSON.h / cJSON.c      # librería JSON de terceros (DaveGamble/cJSON, MIT)
    └── main.c                 # scheduler, pool de hilos, mayoría simple
```

## 7. Pruebas realizadas

- **Compilación**: `gcc -Wall -Wextra -O2` sin errores (sólo warnings
  benignos de `-Wstringop-truncation` en copias de buffers ya acotadas por
  `strncpy` con tamaño explícito).
- **TCP check**: contra un puerto abierto (`127.0.0.1:8123`) reporta
  `healthy=1` con `rtt_ms` medido; contra un puerto cerrado reporta
  `healthy=0` y `rtt_ms=-1`, tal como se espera.
- **HTTP check**: contra un servidor HTTP local que responde `200 OK`,
  reporta `healthy=1` con el RTT del `GET`.
- **Manejo de errores de red**: con `SUPABASE_URL` apuntando a un puerto
  cerrado, el programa registra el error (`rc=7`, *couldn't connect*) y
  continúa su ciclo en vez de terminar abruptamente.

Reproducir:

```bash
make
# TCP/HTTP: levantar un servidor de prueba y correr src/checks.c con un
# pequeño main de prueba, o directamente el binario apuntando a un target real.
```

## 8. Limitaciones conocidas / trabajo pendiente para el equipo

- El nombre exacto de tablas/columnas en Supabase debe confirmarse contra
  lo que efectivamente defina el DNS UI; están centralizados en `main.c`
  para que el ajuste sea de una sola línea por consulta.
- La elección de IP por menor RTT para registros `round-trip` (cruce con
  la tabla *IP to Country* y la IP de origen del cliente) se implementa en
  el **DNS API**, no aquí; este módulo sólo produce y publica el dato de
  RTT por checker/target.
- `http_health_check` usa HTTP simple (no TLS) por defecto: si algún
  target expone HTTPS, basta con pasar `https://` en `hc_host` o adaptar
  la construcción de la URL en `main.c`.