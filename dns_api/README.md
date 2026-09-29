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
| `DNS_REMOTE_TIMEOUT_MS`       | Timeout en milisegundos para consultas UDP                   | `5000`                                                   |
| `SUPABASE_DB_URL`             | Cadena JDBC de conexión a PostgreSQL (puerto 5432 con SSL)   | `jdbc:postgresql://<host>:5432/postgres?sslmode=require` |
| `SUPABASE_DB_USER`            | Usuario de base de datos de Supabase                         | `postgres.<project_ref>`                                 |
| `SUPABASE_DB_PASSWORD`        | Contraseña de base de datos (con percent-encoding si aplica) | `<password>`                                             |
| `DNS_EXECUTOR_CORE_SIZE`      | Hilos base para el pool asíncrono de resolución              | `16`                                                     |
| `DNS_EXECUTOR_MAX_SIZE`       | Hilos máximos para el pool asíncrono                         | `64`                                                     |
| `DNS_EXECUTOR_QUEUE_CAPACITY` | Capacidad de la cola de tareas del pool                      | `200`                                                    |

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
  - `400 Bad Request`: Paquete malformado o no decodificable.
  - `502 Bad Gateway`: Error de comunicación o timeout con el DNS upstream.
  - _Efecto secundario:_ Guarda automáticamente el registro resuelto en la tabla `records` de Supabase.

#### 3. Consultar código de país por IP

- **Método:** `GET`
- **Ruta:** `/api/ip_country?ip={ip_cliente}`
- **Respuesta:**
  - `200 OK` con `{"country_code": "CR"}` o `false` si la IP no coincide con ningún rango.

---

### B. Endpoints CRUD para la DNS UI

#### Registros DNS (`/api/records`)

- `GET /api/records`: Lista todos los registros configurados.
- `POST /api/records`: Crea un nuevo registro DNS (`single`, `multi`, `weight`, `round-trip`, `geo`). Retorna `201 Created`.
- `PUT /api/records/{name}`: Actualiza un registro existente por nombre.
- `DELETE /api/records/{name}`: Elimina un registro por nombre. Retorna `204 No Content`.

#### Rangos de IP a País (`/api/ip_country`)

- `GET /api/ip_country`: Lista completa de rangos CIDR y metadatos de ubicación.
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

## 5. Pruebas Unitarias

Para ejecutar la suite de pruebas automatizadas:

```bash
mvn test
```

Las pruebas cubren:

- Decodificación y validación de paquetes Base64.
- Resolución DNS y persistencia automática en base de datos.
- Normalización de nombres de dominio y operaciones CRUD.
- Manejo de excepciones centralizado.
