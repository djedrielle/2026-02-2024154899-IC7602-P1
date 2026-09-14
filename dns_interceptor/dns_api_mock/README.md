# DNS API Mock

Mock del **DNS API** (que el equipo implementa en Java) para desarrollar el
**DNS Interceptor** sin depender de ese componente. Implementado en Python con
FastAPI, sirve una API REST sobre **HTTPS**.

## Responsabilidades (arquitectura del equipo)

El DNS API **solo entrega datos**; **no** aplica la estrategia de resolución:

- `/api/exists`: devuelve el **registro** de la base de datos si el dominio
  existe, o `false` si no.
- `/api/dns_resolver`: reenvía un paquete DNS (BASE64) a un servidor DNS remoto.

La lógica de los 5 tipos (**single / multi / round-trip / weight / geo**) se
implementa en el **DNS Interceptor**, usando el documento que devuelve
`/api/exists`.

## Estructura

| Archivo | Rol |
|---|---|
| `app.py` | API REST (exists + dns_resolver) |
| `records.json` | Simula la colección `records` (los 5 tipos, con sus atributos) |
| `ip_to_country.json` | Datos de geolocalización (aún **no** expuestos; ver nota) |
| `Dockerfile` | Imagen + certificado HTTPS autofirmado |
| `requirements.txt` | Dependencias Python |

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/exists?domain=<d>` | Registro del dominio, o `false` si no existe |
| `POST` | `/api/dns_resolver` | Reenvía un paquete DNS (BASE64) a un DNS remoto real |
| `GET` | `/` | Info del servicio y dominios cargados |

### Ejemplo de respuesta de `/api/exists`

Dominio existente (`geo.example.com`) — devuelve el documento completo:

```json
{
  "name": "geo.example.com",
  "type": "geo",
  "ttl": 300,
  "ips": [
    { "ip": "10.0.3.1", "country_code": "CR", "healthy": true },
    { "ip": "10.0.3.2", "country_code": "US", "healthy": true },
    { "ip": "10.0.3.3", "country_code": "DE", "healthy": true }
  ]
}
```

Dominio inexistente — devuelve:

```json
false
```

## Construir y levantar (Docker)

```bash
cd dns_interceptor/dns_api_mock
sudo docker build -t dns_api_mock .
sudo docker run --rm -p 8443:8443 dns_api_mock
```

Servidor DNS remoto configurable (para `/api/dns_resolver`):

```bash
sudo docker run --rm -p 8443:8443 -e REMOTE_DNS=1.1.1.1 dns_api_mock
```

## Probar (curl)

El certificado es autofirmado, así que se usa `-k`:

```bash
# registro existente (devuelve el documento)
curl -k "https://localhost:8443/api/exists?domain=weight.example.com"

# dominio inexistente (devuelve false)
curl -k "https://localhost:8443/api/exists?domain=nope.example.com"

# dns_resolver (reenvío real): construir un paquete DNS BASE64 y enviarlo
DATA=$(python3 -c "import base64;print(base64.b64encode(bytes.fromhex('d47601200001000000000001076578616d706c6503636f6d0000010001')).decode())")
curl -k -X POST "https://localhost:8443/api/dns_resolver" \
  -H "Content-Type: application/json" -d "{\"data\":\"$DATA\"}"
```

## Nota: IP to Country (pendiente de decisión)

Los tipos `geo` y `round-trip` que implementarás en el interceptor necesitan
mapear la **source IP** del cliente a un país/ubicación. Esa base
(`IP to Country`) vive en Supabase/Firebase, así que el interceptor tendría que
consultarla a través del DNS API. El archivo `ip_to_country.json` queda listo,
pero **no hay endpoint que lo exponga todavía** — falta decidir cómo se accede a
ese dato (ver conversación).

## Nota para el interceptor (Rust)

- Certificado autofirmado: el cliente HTTP debe aceptar certificados no
  verificados en desarrollo (`reqwest` con `danger_accept_invalid_certs(true)`),
  o confiar en `cert.pem`.
- Apunta el interceptor a la API con una variable de entorno
  (`DNS_API_URL=https://localhost:8443`) para que el cambio mock ↔ API real sea
  solo cambiar esa variable.
