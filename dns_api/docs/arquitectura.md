# Arquitectura del componente DNS API

## 1. Estilo arquitectónico

Se adopta una **arquitectura por capas** (Controller → Service → Interfaces de acceso a infraestructura), con **inversión de dependencias** aplicada de forma explícita: el Service Layer no depende de implementaciones concretas de persistencia ni del cliente UDP, sino de interfaces (`DnsRecordRepository`, `DnsRemoteClient`).

Se descarta un hexagonal completo (Ports & Adapters con separación total de paquetes `domain/`, `application/`, `adapter/in`, `adapter/out`) por decisión de consistencia con el resto del proyecto: los demás componentes (Interceptor en Rust, Health Checker en C, orquestación) no aplican un nivel de formalización equivalente, y sobre-desarrollar la DNS API frente al resto no aporta valor a la nota grupal ni a la evaluación presencial conjunta. El tiempo ganado se invierte en el CRUD para la DNS UI, las validaciones, las pruebas y la documentación, que sí tienen peso directo en la rúbrica. Las estrategias de resolución (single/multi/weight/round-trip/geo) las aplica el DNS Interceptor: el API solo entrega los datos.

Aun así, se conserva el principio central de bajo acoplamiento: **el Service Layer no conoce la tecnología concreta detrás de sus dependencias**, lo cual permite cambiar de proveedor (Supabase → Firebase, o el cliente UDP) sin modificar la lógica de negocio.

![Diagrama de arquitectura](imgs/diagrama.png)

## 2. Capas

### 2.1 Controller Layer

Expone los endpoints REST y delega en los servicios, sin lógica de negocio.

| Endpoint | Método | Responsabilidad |
| -------- | ------ | --------------- |
| `/api/exists?domain=` | GET | Devuelve el registro del dominio, o `false` si no existe (usado por el DNS Interceptor). |
| `/api/dns_resolver` | POST | Recibe un paquete DNS en BASE64, lo reenvía al DNS remoto y devuelve la respuesta en BASE64. |
| `/api/ip_country` | GET, POST, PUT, DELETE | Con `?ip=` devuelve el país de una IP; sin él, lista paginada de rangos. CRUD de rangos para la DNS UI. |
| `/api/records` | GET, POST, PUT, DELETE | CRUD de registros DNS para la DNS UI. |
| `/api/targets` | GET, POST, PUT, DELETE | CRUD de los servidores que revisa el Health Checker. |
| `/api/health_results` | GET, DELETE | Lectura y limpieza del historial que escribe el Health Checker. |

### 2.2 Service Layer

Contiene la lógica de negocio y las validaciones de entrada (paquete `validation`):

- `DnsRecordService`: consulta de registros y, para el tipo `multi`, incremento atómico del contador de round-robin; CRUD de registros.
- `DnsResolverService`: decodifica el BASE64, valida que sea un mensaje DNS (RFC1035), lo envía al DNS remoto y guarda en la base los dominios resueltos que aún no existen (nunca sobrescribe uno existente).
- `IpCountryService`, `TargetService` y `HealthResultService`: consultas y CRUD sobre sus tablas.
- El API **no** aplica las estrategias de resolución (single, multi, weight, round-trip, geo): entrega los datos y el Interceptor decide.

### 2.3 Interfaces de infraestructura (inversión de dependencias)

```java
public interface DnsRemoteClient {
    byte[] resolve(byte[] rawDnsPacket) throws IOException;
}
```

- `DnsRemoteClient` → `DnsjavaRemoteClient`, cliente UDP dentro del mismo proceso Spring Boot (`@Component`, sin contenedor separado, ya que no se reutiliza fuera de la API ni requiere escalado independiente). Reintenta ante un paquete perdido.
- Repositorios (`DnsRecordRepository`, `IpToCountryRepository`, `TargetRepository`, `HealthResultRepository`) → interfaces de Spring Data JPA hacia Supabase; Spring genera la implementación.

## 3. Flujo general de una petición `/api/dns_resolver`

1. El Controller recibe el POST con el campo `data` en BASE64.
2. El Service lo decodifica y comprueba que sea un mensaje DNS válido; si no, responde `400`.
3. La resolución se ejecuta en un pool de hilos (`dnsResolverExecutor`), de modo que muchas peticiones avanzan a la vez.
4. `DnsRemoteClient.resolve(...)` envía el paquete por UDP al servidor DNS remoto, con reintentos. Si no hay respuesta, `502`.
5. Si la respuesta trae direcciones A y el dominio no existe en `records`, el Service lo guarda.
6. El Controller codifica la respuesta en BASE64 y la devuelve.

El DNS Interceptor consulta antes `GET /api/exists`: si el dominio está registrado aplica su estrategia con los datos recibidos, y solo si no existe (o no hay IPs saludables) llama a `/api/dns_resolver`.

## 4. Consideraciones de diseño

- **Configuración externa**: IP del servidor DNS remoto y credenciales de Supabase/Firebase inyectadas por variables de entorno, sin valores quemados en código.
- **Concurrencia**: tanto el Controller (pool de hilos por defecto de Spring Boot) como el cliente UDP deben soportar múltiples solicitudes simultáneas.
- **Extensibilidad controlada**: las interfaces permiten cambiar de proveedor de base de datos sin tocar Service ni Controller, sin necesidad de la estructura completa de paquetes de un hexagonal.
