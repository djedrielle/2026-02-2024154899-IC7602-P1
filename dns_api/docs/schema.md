# Esquema de la base de datos — Supabase

**Proyecto 1 · IC7602 Redes · 2026-02**

Estado del esquema al 17/09/2026. Este documento describe las tablas que existen
hoy en Supabase, el contrato del campo `ips`, y los puntos que siguen sin
definirse.

---

## 1. Tablas existentes

### `records`

Registros DNS que el sistema resuelve localmente.

| Columna | Tipo    | Nullable | Notas                                                                  |
| ------- | ------- | -------- | ---------------------------------------------------------------------- |
| `name`  | `text`  | NO       | **PK**. Nombre del dominio, sin punto final (ej. `single.example.com`) |
| `type`  | `text`  | NO       | Uno de: `single`, `multi`, `weight`, `round-trip`, `geo`               |
| `ttl`   | `int4`  | NO       | TTL en segundos                                                        |
| `ips`   | `jsonb` | NO       | Array de IPs. Ver sección 2                                            |
| `counter` | `int4` | NO      | Contador de round-robin (por defecto `0`). Solo avanza en registros `multi` |

### `targets`

Servidores que revisa el Health Checker. Único por (`record_name`, `ip_address`).

| Columna                 | Tipo      | Nullable | Notas                                          |
| ----------------------- | --------- | -------- | ---------------------------------------------- |
| `id`                    | `uuid`    | NO       | **PK**                                         |
| `record_name`           | `text`    | NO       | FK a `records.name`                            |
| `ip_address`            | `text`    | NO       | IP a revisar                                   |
| `port`                  | `int4`    | NO       | 1-65535                                        |
| `check_type`            | `text`    | NO       | `TCP` o `HTTP`                                 |
| `timeout_ms`, `retries` | `int4`    | NO       | Timeout por intento y cantidad de intentos     |
| `http_path`             | `text`    | SÍ       | Solo HTTP; empieza con `/`                     |
| `expected_status_codes` | `int4[]`  | SÍ       | Solo HTTP; códigos HTTP aceptados              |
| `basic_auth_user`, `basic_auth_pass` | `text` | SÍ | Credenciales basic auth opcionales          |

### `health_results`

Historial de chequeos: una fila por prueba y ubicación del Health Checker.

| Columna                                   | Tipo          | Notas                                           |
| ----------------------------------------- | ------------- | ----------------------------------------------- |
| `id`                                      | `int8`        | **PK**                                          |
| `target_id`, `record_name`, `ip_address`  | `uuid`/`text` | Qué se probó                                    |
| `is_healthy`, `latency_ms`                | `bool`/`float8` | Resultado por mayoría simple y latencia media |
| `checker_location_id`, `checker_country`, `checker_city`, `checker_latitude`, `checker_longitude` | `text`/`float8` | Ubicación (simulada) del Health Checker |
| `checked_at`                              | `timestamptz` | Momento de la prueba                            |

### `ip_to_country`

Base de datos IP → país, usada para los tipos `geo` y `round-trip`.

| Columna        | Tipo     | Nullable | Notas                                 |
| -------------- | -------- | -------- | ------------------------------------- |
| `id`           | `int8`   | NO       | **PK**                                |
| `start_ip`     | `inet`   | NO       | Inicio del rango                      |
| `end_ip`       | `inet`   | NO       | Fin del rango                         |
| `country_code` | `bpchar` | NO       | Código de país (ej. `CR`, `US`, `ZZ`) |
| `country_name` | `text`   | SÍ       |                                       |
| `city`         | `text`   | SÍ       |                                       |
| `latitude`     | `float8` | SÍ       |                                       |
| `longitude`    | `float8` | SÍ       |                                       |

> **Nota sobre `country_code`:** es `bpchar` (character de ancho fijo), no
> `varchar`. Si mapean esta columna con un ORM, hay que declararlo
> explícitamente o la validación de esquema falla.

---

## 2. Contrato del campo `ips` (jsonb)

`ips` es un **array de objetos**. Todos los objetos llevan `ip` y `healthy`;
los campos adicionales dependen del valor de `type` en el registro.

| `type`       | Campos de cada objeto                    |
| ------------ | ---------------------------------------- |
| `single`     | `ip`, `healthy`                          |
| `multi`      | `ip`, `healthy`                          |
| `weight`     | `ip`, `healthy`, `weight`                |
| `round-trip` | `ip`, `healthy`, `latitude`, `longitude` |
| `geo`        | `ip`, `healthy`, `country_code`          |

### Ejemplos

```json
// single
[{ "ip": "93.184.216.34", "healthy": true }]
```

```json
// multi
[
  { "ip": "10.0.0.1", "healthy": true },
  { "ip": "10.0.0.2", "healthy": false }
]
```

```json
// weight
[
  { "ip": "10.0.1.1", "weight": 70, "healthy": true },
  { "ip": "10.0.1.2", "weight": 20, "healthy": true },
  { "ip": "10.0.1.3", "weight": 10, "healthy": true }
]
```

```json
// round-trip
[
  { "ip": "10.0.2.1", "latitude": 9.93, "longitude": -84.08, "healthy": true },
  { "ip": "10.0.2.2", "latitude": 40.71, "longitude": -74.01, "healthy": true }
]
```

```json
// geo
[
  { "ip": "10.0.3.1", "country_code": "CR", "healthy": true },
  { "ip": "10.0.3.2", "country_code": "US", "healthy": true }
]
```
