# Despliegue automatizado

El despliegue **oficial** del proyecto es en **Kubernetes** y se ejecuta con un solo comando: `make`.
Docker Compose existe como alternativa **para desarrollo y pruebas locales** (`make dev-up`).

> **Kubernetes y Compose nunca deben correr a la vez.** Usan los mismos puertos del host (8080, 3000…) y comparten
> el límite de conexiones de Supabase (15 en el Session Pooler). Por eso cada uno detiene al otro al arrancar:
> `make` detiene Compose y `make dev-up` elimina el despliegue de Kubernetes.

```bash
make help        # lista todos los comandos
```

## 1. Requisitos

| Herramienta | Para qué | Nota |
| ----------- | -------- | ---- |
| Docker | Construir las imágenes | Docker Desktop o Docker Engine |
| `kubectl` y un cluster local | Despliegue oficial | Probado con el Kubernetes de Docker Desktop (Settings → Kubernetes → Enable) |
| `make` | Ejecutar el `Makefile` | Funciona con GNU Make 3.81 (la versión de macOS) |
| `openssl` | Generar la contraseña del keystore TLS | |
| Credenciales de Supabase | Ambos caminos | URL, usuario y contraseña del **Session Pooler** (puerto 5432) |

## 2. Inicio rápido (despliegue oficial)

```bash
make            # equivale a "make k8s-start"
```

Es un solo comando. En orden, `make`:

1. detiene Docker Compose si está corriendo (para liberar los puertos);
2. crea `dns_api/.env` y `health_checker/.env` desde sus `.env.example`, y agrega las variables nuevas que falten;
3. completa las credenciales de Supabase (ver abajo) y genera el `DATABASE_URL` del Health Checker;
4. construye las 4 imágenes;
5. crea los Secrets y aplica los manifiestos de Kubernetes (los 4 módulos, con los 2 Health Checkers encendidos) y espera a que los pods estén listos;
6. prueba API (HTTP y HTTPS), UI e interceptor;
7. **abre la UI en el navegador**: <http://localhost:30080>.

**Credenciales de Supabase.** Se toman, en este orden, de:

- **variables de entorno**, útil para automatizar sin terminal interactiva:

  ```bash
  SUPABASE_DB_URL='jdbc:postgresql://<host>:5432/postgres?sslmode=require' \
  SUPABASE_DB_USER='postgres.<project_ref>' \
  SUPABASE_DB_PASSWORD='<password>' \
  make
  ```
- **un prompt en la terminal**: si no están definidas, `make` las pide (la contraseña no se muestra);
- **el archivo `dns_api/.env`**, si ya las escribiste ahí.

Quedan guardadas en `dns_api/.env`, que **no se versiona**: la siguiente vez `make` ya no las pide.
Si no hay variables ni terminal, `make` se detiene y explica qué falta.

## 3. Kubernetes (oficial)

| Comando | Qué hace |
| ------- | -------- |
| `make` / `make k8s-start` | Todo el flujo anterior: configura, construye, despliega, prueba y abre la UI. |
| `make k8s-up` | Lo mismo sin detener Compose, sin probar y sin abrir el navegador. |
| `make k8s-build` | Construye `dns-api`, `dns-ui`, `dns-interceptor` y `health-checker` con el tag `:local`. La UI recibe `NEXT_PUBLIC_DNS_API_URL` como `--build-arg` (variable `UI_API_URL`). |
| `make k8s-secrets` | Crea o actualiza `dns-api-secret` y `health-checker-secret` desde los `.env`. Conserva la contraseña del keystore TLS entre ejecuciones. |
| `make k8s-deploy` | Aplica namespace, ConfigMaps, Deployments y Services, reinicia los pods para que usen las imágenes recién construidas y espera a que estén listos. |
| `make k8s-status` | Pods y Services del namespace `dns`. |
| `make k8s-test` | Espera al API y prueba API por HTTP y HTTPS, UI e interceptor (resuelve el MX de gmail.com). |
| `make k8s-open` | Abre la UI en el navegador (`UI_URL`, por defecto <http://localhost:30080>). |
| `make k8s-logs S=dns-api` | Últimas 100 líneas de un servicio. |
| `make k8s-checkers-on` / `make k8s-checkers-off` | Enciende o apaga los dos Health Checkers sin redesplegar el resto. |
| `make k8s-down` | Borra el namespace completo (pods, servicios y secrets). |

### 3.1 Qué se despliega

| Módulo | Deployment | Acceso desde el host |
| ------ | ---------- | -------------------- |
| DNS API | `dns-api` (Service `LoadBalancer`) | http://localhost:8080 y https://localhost:8443 |
| DNS UI | `dns-ui` (`NodePort`) | http://localhost:30080 |
| DNS Interceptor | `dns-interceptor` (`NodePort`, UDP) | `nslookup -port=30053 <dominio> 127.0.0.1` |
| Health Checker | `health-checker-cr` y `health-checker-us` | (sin puerto; escriben en Supabase) |

Dentro del cluster el interceptor llega al API por `https://dns-api:8443` (nombre del Service).

### 3.2 Configuración: ConfigMap y Secret

Ningún valor de configuración está en el código; todo entra por variables de entorno.

| Recurso | Contenido |
| ------- | --------- |
| `dns-api-config` (ConfigMap) | Puertos, `DNS_UI_ORIGIN`, DNS remoto y timeout, `DB_POOL_SIZE`, opciones de HTTPS. |
| `dns-api-secret` (Secret) | `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`, `SSL_KEYSTORE_PASSWORD`. |
| `dns-interceptor-config` | `DNS_API_URL`. |
| `health-checker-config` | `CHECK_INTERVAL_SECONDS`. La ubicación de cada checker (id, país, ciudad, latitud, longitud) va en su Deployment. |
| `health-checker-secret` | `DATABASE_URL` (URI de `libpq`). |

Los Secrets reales **nunca se versionan**: `make k8s-secrets` los crea desde tus `.env`. Los archivos
`k8s/*/secret.example.yaml` son solo plantillas de referencia y el `Makefile` no los aplica.

### 3.3 Health Checkers

Se despliegan **encendidos**: son parte del sistema y cada uno prueba los targets desde su ubicación simulada,
`CR-01` (Cartago) y `US-01` (Nueva York), para el tipo `round-trip`.

Escriben en la base compartida: insertan filas en `health_results` y cambian el `healthy` de `records.ips`. Con los
targets de ejemplo (`10.0.x.x`, `1.2.3.4`) casi todo pasará a `unhealthy`, y el interceptor no responde con IPs
caídas. Para desplegar sin ellos: `make CHECKERS=off`. Para apagarlos o encenderlos después:
`make k8s-checkers-off` y `make k8s-checkers-on`.

### 3.4 Otros clusters

- **kind:** `make KIND_CLUSTER=<nombre>` carga las imágenes en el cluster tras construirlas.
- **minikube:** ejecuta antes `eval $(minikube docker-env)` para construir dentro de su Docker.
- El `Service` del API es `LoadBalancer`, que solo se publica en `localhost` en Docker Desktop. En otros clusters
  usa `kubectl -n dns port-forward svc/dns-api 8080:8080` o cambia el tipo a `NodePort`.

## 4. Docker Compose (desarrollo y pruebas locales)

Alternativa para desarrollar sin Kubernetes. **Al levantarse, elimina el despliegue de Kubernetes** (namespace `dns`).

| Comando | Qué hace |
| ------- | -------- |
| `make env` | Crea los `.env` que falten desde los `.env.example` y agrega las variables nuevas. |
| `make dev-up` | Configura (`.env`, credenciales, `DATABASE_URL`) y ejecuta `docker compose up --build -d`. Con `SERVICES="dns-api dns-ui"` levanta solo esos servicios. |
| `make dev-test` | Espera al API (hasta 2 minutos) y prueba API, UI e interceptor. |
| `make dev-ps` / `make dev-logs [S=dns-api]` | Estado y logs de los contenedores. |
| `make dev-down` | Detiene y elimina los contenedores. |

Servicios y puertos (host):

| Servicio | URL | Nota |
| -------- | --- | ---- |
| DNS API | http://localhost:8080 | Solo HTTP (el HTTPS se activa con `SSL_ENABLED=true`, ver sección 5) |
| DNS UI | http://localhost:3000 | `DNS_UI_ORIGIN` del API coincide con este origen |
| DNS Interceptor | UDP `localhost:15353` | `nslookup -port=15353 example.org 127.0.0.1` |
| Health Checker | (sin puerto) | Escribe resultados en Supabase cada `CHECK_INTERVAL_SECONDS` |

> El Health Checker de Compose **escribe en Supabase y actualiza el estado `healthy` de los registros**. Con IPs de
> ejemplo (`10.0.x.x`, `1.2.3.4`) casi todo pasará a `unhealthy`. Para levantar todo menos el checker:
> `make dev-up SERVICES="dns-api dns-ui dns-interceptor"`.
>
> Los contenedores de Compose tienen `restart: unless-stopped`: si reinicias Docker Desktop **vuelven a arrancar solos**
> aunque los hayas detenido con otro comando. Si vas a usar Kubernetes, ejecuta `make dev-down` o simplemente `make`.

## 5. HTTPS del DNS API

El enunciado pide que el interceptor consuma el API por HTTPS. En Kubernetes el API arranca con `SSL_ENABLED=true`:
sirve HTTPS en 8443 (para el interceptor) y mantiene HTTP en 8080 (para la UI, porque un navegador no acepta un
certificado autofirmado sin intervención). El certificado autofirmado lo genera `docker-entrypoint.sh` al arrancar el
contenedor, con la contraseña del Secret. Para probarlo: `curl -k https://localhost:8443/api/records`.

## 6. Solución de problemas

| Síntoma | Causa | Solución |
| ------- | ----- | -------- |
| `Faltan credenciales de Supabase` | No hay variables de entorno ni terminal interactiva para pedirlas | Definir `SUPABASE_DB_URL`, `SUPABASE_DB_USER` y `SUPABASE_DB_PASSWORD` al invocar `make`, o escribirlas en `dns_api/.env` |
| `No hay un cluster accesible con kubectl` | Kubernetes no está activado | Docker Desktop → Settings → Kubernetes → Enable |
| Pod `dns-api` en `CrashLoopBackOff` con `EMAXCONNSESSION` | El Session Pooler de Supabase admite 15 conexiones en total y está lleno | Cerrar otros clientes: Compose (`make dev-down`), otro API, checkers, `psql`. El pool del API es `DB_POOL_SIZE` (5 en k8s) |
| Pod `dns-api` en `CrashLoopBackOff` con `UnknownHostException` justo tras reiniciar Docker Desktop | El cluster aún no resolvía nombres | Se recupera solo en unos segundos |
| `dns-api` `EXTERNAL-IP <pending>` | El puerto 8080 del host está ocupado (p. ej. por Compose) | `make` (detiene Compose) o `make dev-down` |
| `ErrImagePull` / `ImagePullBackOff` | La imagen `:local` no existe en el cluster | `make k8s-build`; con kind, usar `KIND_CLUSTER` |
| El API no arranca: `Could not resolve placeholder 'DNS_UI_ORIGIN'` | Un `.env` antiguo sin esa variable | `make env` la agrega desde `.env.example` |
| `nslookup` da timeout con `single.example.com` | Un Health Checker marcó su IP como `unhealthy` y el interceptor no responde con IPs caídas | Revisar/restaurar `healthy` con `PUT /api/records/{name}` |
| Fallos ocasionales `502` en `/api/dns_resolver` | Docker Desktop pierde ~10 % de paquetes UDP; el API reintenta una vez (`DNS_REMOTE_RETRIES`) | Aumentar `DNS_REMOTE_RETRIES` |

## 7. Uso de IA y problemas encontrados

> Esta sección documenta el uso de IA generativa en la parte de automatización, como pide el enunciado.
> Revisar y completar con los prompts propios de cada integrante.

**Herramienta:** Claude (Sonnet 5 y 5.5) mediante Claude Code, trabajando sobre el repositorio.

**Prompts principales (resumidos):**

1. "Necesito que cada módulo tenga su Dockerfile y en la raíz un docker compose que levante los contenedores de
   cada módulo. ¿Un Dockerfile es un contenedor? ¿O un docker compose es un contenedor?"
2. "Tengo un proyecto con 4 módulos (interceptor en Rust, API en Java, health checker en C, UI en React), cada uno con
   su Dockerfile y un docker-compose en la raíz. Quiero desplegarlo en Kubernetes local con manifiestos YAML planos,
   sin Helm. Primero dime qué cluster local recomiendas y cómo cargar las imágenes."
3. "Genera el Deployment, el Service, el ConfigMap y el Secret del dns-api con las variables del compose." (y las
   peticiones equivalentes para dns-interceptor, health-checker y dns-ui, con NodePort fijo, probes válidas para UDP
   y un Deployment por ubicación con lat, lon, país y ciudad distintos).
4. "¿Cómo automatizo Kubernetes?" → "Escríbelo con el Makefile y el docu/k8s.md."
5. "Aún no está automatizado" (con la salida de `make`, que solo mostraba la ayuda) → el `Makefile` pasó a ejecutar
   todo el flujo con un solo comando.
6. "Ocupo un comando que levante todo desde k8s, automatizado y abra la URL de la UI directamente." y "no deben estar
   los dos a la vez: la automatización oficial es la de Kubernetes y la final; la de Compose existe como manera de
   desarrollo" → `make` despliega Kubernetes y abre la UI; Compose pasó a `make dev-*` y cada stack detiene al otro.

**Problemas encontrados y cómo se resolvieron:**

| Problema | Resolución |
| -------- | ---------- |
| Un `.gitignore` de la raíz ignoraba `*/dns_api_mock` y ocultaba archivos nuevos | Se quitó esa regla |
| El build de Maven fallaba porque un test no compilaba (`RecordResponse` cambió de constructor) | Se corrigió el test |
| El API no arrancaba: Hibernate rechazaba la columna `int[]` de `targets` (`ddl-auto: validate`) | Se cambió el mapeo a `@JdbcTypeCode(SqlTypes.ARRAY)` |
| `PUT /api/records` daba 500: el código ponía `counter = null` y la columna es `NOT NULL` | El contador vale 0 para todos los tipos |
| El puerto 8080 estaba ocupado por un contenedor viejo de Compose y el `LoadBalancer` quedaba en `<pending>` | Se detuvo el contenedor; ahora `make` detiene Compose antes de desplegar |
| El pod nuevo del API entraba en `CrashLoopBackOff` durante un rolling update (`EMAXCONNSESSION`): el pooler de Supabase admite 15 conexiones | Estrategia `Recreate` y `DB_POOL_SIZE` configurable |
| Los logs del Health Checker no aparecían en `kubectl logs` (el `printf` de C queda en buffer al escribir a un pipe) | El contenedor se ejecuta con `stdbuf -oL` |
| Probes: `tcpSocket` no sirve para UDP | El interceptor usa una probe `exec` sobre `/proc/net/udp` |
| Los Health Checkers marcaron `unhealthy` casi todos los registros de ejemplo y el DNS dejó de resolverlos | Se restauraron los flags. Los checkers son parte del sistema, así que se despliegan encendidos (`make CHECKERS=off` los omite) |
| El Health Checker no se creaba con `make`: se había dejado apagado por defecto | `make` los despliega encendidos |
| Tras cada despliegue se veían contenedores duplicados durante ~35 s (interceptor y health-checker): el proceso corre como PID 1 sin manejar `SIGTERM` y Kubernetes espera los 30 s de gracia antes de matarlo | Solo es visual; se puede reducir con `terminationGracePeriodSeconds` |
| Docker Desktop pierde ~10 % de paquetes UDP hacia el DNS remoto (`502` intermitentes) | Reintentos en el cliente UDP del API |
| Un `.env` local antiguo no tenía `DNS_UI_ORIGIN` y el API de Compose no arrancaba | `make env` agrega las variables nuevas de los `.env.example` |
| Kubernetes y Compose corriendo a la vez se estorbaban (puertos y conexiones a Supabase), y al reiniciar Docker Desktop los contenedores de Compose volvían solos | Kubernetes es el camino oficial; cada stack detiene al otro |
| `make` a secas solo mostraba la ayuda, y había que crear y editar los `.env` a mano | `make` ejecuta todo el flujo; las credenciales se leen de variables de entorno o se piden por consola |
| El `WORKDIR` del Dockerfile del Health Checker cambió a `/health_checker` | Los manifiestos usan rutas relativas y no se vieron afectados |
