# DNS API — Componente Backend (IC7602 Proyecto 1)

API REST desarrollada en **Java 21** con **Spring Boot 3.4.0** que actúa como el backend central del sistema DNS, conectando el **DNS Interceptor**, la **DNS UI** y el **Health Checker** con la base de datos PostgreSQL administrada en **Supabase**.

---

## 1. Características Principales

- **Resolución Local (`/api/exists`):** Consulta dominios registrados en Supabase para soportar balanceo (_single_, _multi/round-robin_, _weight_, _round-trip_, _geo_).
- **Resolución Recursiva Remota (`/api/dns_resolver`):** Reenvía paquetes DNS codificados en Base64 vía UDP (con pool de hilos asíncrono) hacia un servidor upstream (por defecto Google DNS `8.8.8.8:53`) y almacena automáticamente los dominios resueltos en la base de datos.
- **Geolocalización por IP (`/api/ip_country`):** Permite al DNS Interceptor identificar el país de origen de una IP cliente para aplicar políticas de enrutamiento geográfico.
- **Gestión para la DNS UI:** Endpoints CRUD para administración de registros DNS, mapeos de IP a país y targets de servidores backend.
- **Integración con Health Checker:** Gestión de targets y almacenamiento/consulta de resultados de chequeos de salud y latencias.
- **CORS Global:** Configurado para permitir peticiones desde la interfaz gráfica web.

---

## 2. Variables de Entorno

Crear un archivo `.env` en la raíz de `dns_api/` a partir de `.env.example`:

| Variable                      | Descripción                                                  | Valor por defecto / Ejemplo                              |
| :---------------------------- | :----------------------------------------------------------- | :------------------------------------------------------- |
| `SERVER_PORT`                 | Puerto HTTP del servicio                                     | `8080`                                                   |
| `DNS_REMOTE_HOST`             | Host del servidor DNS remoto upstream                        | `8.8.8.8`                                                |
| `DNS_REMOTE_PORT`             | Puerto del servidor DNS remoto upstream                      | `53`                                                     |
| `DNS_REMOTE_TIMEOUT_MS`       | Timeout en milisegundos **por intento** de consulta UDP      | `5000`                                                   |
| `DNS_REMOTE_RETRIES`          | Reintentos ante un paquete UDP perdido o sin respuesta       | `1`                                                      |
| `SUPABASE_DB_URL`             | Cadena JDBC de conexión a PostgreSQL (puerto 5432 con SSL)   | `jdbc:postgresql://<host>:5432/postgres?sslmode=require` |
| `SUPABASE_DB_USER`            | Usuario de base de datos de Supabase                         | `postgres.<project_ref>`                                 |
| `SUPABASE_DB_PASSWORD`        | Contraseña de base de datos (con percent-encoding si aplica) | `<password>`                                             |
| `DB_POOL_SIZE`                | Conexiones máximas del pool a la base (el pooler de Supabase admite 15 en total) | `10`                                                     |
| `DNS_EXECUTOR_CORE_SIZE`      | Hilos base para el pool asíncrono de resolución              | `16`                                                     |
| `DNS_EXECUTOR_MAX_SIZE`       | Hilos máximos para el pool asíncrono                         | `64`                                                     |
| `DNS_EXECUTOR_QUEUE_CAPACITY` | Capacidad de la cola de tareas del pool                      | `200`                                                    |
| `SSL_ENABLED`                 | Activa HTTPS en `SERVER_PORT` (ver sección 3.1)              | `false`                                                  |
| `HTTP_PORT`                   | Puerto HTTP adicional cuando `SSL_ENABLED=true` (`0` = solo HTTPS) | `0`                                                |
| `SSL_KEYSTORE_PATH`           | Keystore PKCS12; si no existe se genera uno autofirmado      | `/tmp/dns-api-keystore.p12`                              |
| `SSL_KEYSTORE_PASSWORD`       | Contraseña del keystore (obligatoria con `SSL_ENABLED=true`) | `<cadena-aleatoria>`                                     |
| `SSL_CERT_CN`                 | Nombre (CN/SAN) del certificado autofirmado                  | `dns-api`                                                |
| `DNS_UI_ORIGIN`               | Origen permitido por CORS: la URL desde la que se abre la DNS UI | `http://localhost:3000`                              |

---

## 3. Ejecución del Proyecto

### Opción A: Mediante Docker Compose (Recomendado)

```bash
# Dentro del directorio dns_api
docker compose up --build -d
```

Para detener el servicio:

```bash
docker compose down
```

### Opción B: Ejecución Local con Maven

Requisitos: Java 21 y Maven 3.9+.

```bash
# Exportar las variables de entorno necesarias o cargarlas desde .env
export $(cat .env | xargs)

# Compilar y ejecutar
mvn spring-boot:run
```

---

### 3.1 HTTPS

El enunciado pide que el DNS Interceptor consuma el API por HTTPS. Con `SSL_ENABLED=true` el API sirve
HTTPS en `SERVER_PORT` (por ejemplo `8443`) y, si `HTTP_PORT` es mayor que 0, mantiene además un puerto HTTP
(por ejemplo `8080`) para la DNS UI, porque un navegador no acepta llamadas a un certificado autofirmado.

Al arrancar, `docker-entrypoint.sh` genera un keystore autofirmado con `keytool` (con `dns-api`, `localhost` y
`127.0.0.1` como nombres alternativos) si el archivo de `SSL_KEYSTORE_PATH` no existe. Para usar un certificado
propio, monta tu keystore PKCS12 en esa ruta.

```bash
curl -k https://localhost:8443/api/exists?domain=example.com   # -k: el certificado es autofirmado
```

El DNS Interceptor acepta el certificado autofirmado (solo desarrollo). Sin `SSL_ENABLED` el API responde solo
por HTTP, como antes.

---

## 4. Referencia de Endpoints

### A. Endpoints para el DNS Interceptor

#### 1. Verificar si un dominio existe localmente

- **Método:** `GET`
- **Ruta:** `/api/exists?domain={nombre_dominio}`
- **Respuesta:**
  - `200 OK` con el objeto del registro DNS si existe (con IPs, tipo, TTL y contador round-robin).
  - `200 OK` con `false` si el dominio no está registrado localmente.

#### 2. Resolver dominio contra DNS remoto recursivo

- **Método:** `POST`
- **Ruta:** `/api/dns_resolver`
- **Request Body:**
  ```json
  {
    "data": "<PAQUETE_DNS_EN_BASE64>"
  }
  ```
- **Respuesta:**
  - `200 OK`:
    ```json
    {
      "data": "<RESPUESTA_DNS_EN_BASE64>"
    }
    ```
  - `400 Bad Request`: `data` ausente, no es BASE64 válido o no contiene un paquete DNS válido.
  - `502 Bad Gateway`: Error de comunicación o timeout con el DNS upstream, después de agotar los reintentos (`DNS_REMOTE_RETRIES`).
  - _Efecto secundario:_ si la respuesta trae direcciones A y el dominio **no existe** en `records`, lo guarda (`single` con una IP, `multi` con varias). Un dominio que ya existe **nunca se sobrescribe**: puede haberlo definido un usuario con su tipo, pesos o países.

#### 3. Consultar código de país por IP

- **Método:** `GET`
- **Ruta:** `/api/ip_country?ip={ip_cliente}`
- **Respuesta:**
  - `200 OK` con `{"country_code": "CR"}` o `false` si la IP no coincide con ningún rango.
  - `400 Bad Request` si `ip` no es una IP IPv4/IPv6 válida.

---

### B. Endpoints CRUD para la DNS UI

#### Registros DNS (`/api/records`)

- `GET /api/records`: Lista todos los registros configurados.
- `POST /api/records`: Crea un nuevo registro DNS (`single`, `multi`, `weight`, `round-trip`, `geo`). Retorna `201 Created`.
- `PUT /api/records/{name}`: Actualiza un registro existente por nombre.
- `DELETE /api/records/{name}`: Elimina un registro por nombre. Retorna `204 No Content`.

#### Rangos de IP a País (`/api/ip_country`)

- `GET /api/ip_country?page=0&size=100`: Lista paginada de rangos (ordenada por `id`; `size` de 1 a 1000, por defecto 100). La tabla tiene cientos de miles de filas, por eso nunca se devuelve completa.
- `POST /api/ip_country`: Crea un nuevo mapeo IP → país. Retorna `201 Created`.
- `PUT /api/ip_country/{id}`: Modifica un rango por su ID numérico.
- `DELETE /api/ip_country/{id}`: Elimina un rango por ID. Retorna `204 No Content`.

#### Servidores Destino / Targets (`/api/targets`)

- `GET /api/targets`: Lista los targets (permite filtrar con `?record_name={name}`).
- `POST /api/targets`: Registra un target asociado a un registro DNS. Retorna `201 Created`.
- `PUT /api/targets/{id}`: Modifica un target por UUID.
- `DELETE /api/targets/{id}`: Elimina un target por UUID.
- `DELETE /api/targets?record_name={name}`: Elimina todos los targets de un dominio.

#### Resultados de Chequeos de Salud (`/api/health_results`)

- `GET /api/health_results`: Lista reportes de salud (permite filtrar con `?record_name={name}` o `?target_id={uuid}`).
- `DELETE /api/health_results/{id}`: Elimina un resultado de salud por su ID.
- `DELETE /api/health_results?record_name={name}`: Limpia el historial de un registro.

---

## 5. Validaciones y formato de errores

Todos los errores tienen el formato `{"error": "mensaje"}`. La API valida antes de escribir y no expone SQL.

| Código | Cuándo |
| :----- | :----- |
| `400`  | Datos inválidos: `name` vacío o mal formado; `type` distinto de `single`, `multi`, `weight`, `round-trip`, `geo`; `ttl` ausente o negativo; `ips` vacía o con una IP inválida; `weight`/`latitude`/`longitude`/`country_code` ausentes en los tipos que los exigen; rangos `ip_country` invertidos o de familias distintas; targets con `port` fuera de 1-65535, `check_type` distinto de `TCP`/`HTTP` o `http_path` sin `/`; JSON ilegible; parámetros con tipo incorrecto; un target de un registro DNS que no existe. |
| `404`  | El registro, rango o target indicado no existe. |
| `409`  | El registro ya existe, o el target ya existe para ese registro y esa IP. |
| `502`  | El DNS remoto no respondió tras los reintentos. |
| `503`  | El pool de resolución está saturado. |

---

## 6. Pruebas

### Unitarias

```bash
mvn test
```

La suite tiene **130 pruebas** y cubre el **93 %** de las líneas y de las ramas (medido con JaCoCo).
Incluye: decodificación y validación de paquetes DNS, resolución con reintentos y persistencia sin
sobrescritura, CRUD de registros, rangos IP-a-país (con paginación), targets y resultados de salud, los
validadores de entrada, la configuración del pool y de HTTPS, y el manejador central de excepciones.

Para medir la cobertura: `mvn org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.12:report`
(el informe queda en `target/site/jacoco/index.html`).

### Manuales

`docs/DNS_API.postman_collection.json` incluye 31 peticiones que cubren todos los endpoints, con ejemplos de
respuesta y de error. Variables: `base_url` (HTTP), `base_url_https` (HTTPS) y los ids para PUT/DELETE.
