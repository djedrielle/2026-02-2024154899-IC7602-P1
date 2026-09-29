# Apuntes — DNS: RFC1035 y RFC2929

## RFC1035 — Formato de mensajes DNS

- Publicado en 1987. Define la codificación binaria exacta de los mensajes DNS.
- Transporte: UDP por defecto. TCP si excede ese límite o para transferencias de zona.
- Todo mensaje tiene 5 secciones: **Header, Question, Answer, Authority, Additional**.

### Header (12 bytes)

| Campo                           | Tamaño      | Significado                                     |
| ------------------------------- | ----------- | ----------------------------------------------- |
| ID                              | 16 bits     | Correlaciona pregunta-respuesta                 |
| QR                              | 1 bit       | 0=consulta, 1=respuesta                         |
| OPCODE                          | 4 bits      | 0=QUERY estándar, 1=IQUERY (obsoleto), 2=STATUS |
| AA                              | 1 bit       | Respuesta autoritativa                          |
| TC                              | 1 bit       | Mensaje truncado                                |
| RD                              | 1 bit       | Recursión solicitada                            |
| RA                              | 1 bit       | Recursión disponible                            |
| Z                               | 3 bits      | Reservado (debe ser 0)                          |
| RCODE                           | 4 bits      | 0=sin error, 3=NXDOMAIN, etc.                   |
| QDCOUNT/ANCOUNT/NSCOUNT/ARCOUNT | 16 bits c/u | Cantidad de entradas por sección                |

Query estándar = QR=0 y OPCODE=0.

### Question

QNAME (nombre consultado), QTYPE (tipo de registro), QCLASS (normalmente IN).

### Resource Record (Answer/Authority/Additional)

NAME, TYPE, CLASS, TTL, RDLENGTH, RDATA. RDLENGTH indica cuántos bytes leer de RDATA; su contenido depende del TYPE (ej. tipo A = 4 bytes de IPv4).

### Codificación de nombres

Secuencia de labels con prefijo de longitud, terminada en byte 0x00. Ejemplo: `03 www 07 ejemplo 03 com 00`.

**Compresión**: puntero de 2 bytes con los 2 bits más significativos en `11`; los 14 bits restantes son el offset al nombre ya presente en el mensaje. Obligatorio de soportar en cualquier parser real.

### Tipos de registro clave

A(1), NS(2), CNAME(5), SOA(6), PTR(12), MX(15), TXT(16), AAAA(28, agregado después).

---

## RFC2929 — Gobernanza de parámetros DNS

- No define el protocolo; define cómo se asignan y administran los valores numéricos usados dentro de los mensajes (TYPE, CLASS, OPCODE, RCODE).
- Necesario porque estos campos tienen tamaño fijo y finito; requiere un proceso ordenado de asignación para evitar conflictos entre implementaciones.

### Espacios que regula

- **RR TYPEs**: valores asignados vs. privados/experimentales. Distingue QTYPE de RRTYPE.
- **CLASSes**: IN es la única de uso universal; CH y HS son históricas; NONE/ANY se usan en updates dinámicos.
- **OPCODEs**: espacio de 4 bits. 0=QUERY, 1=IQUERY (obsoleto), 2=STATUS.
- **RCODEs**: 0=NoError, 1=FormErr, 2=ServFail, 3=NXDomain, 4=NotImp, 5=Refused. Ampliado de 4 a 12 bits con EDNS0.

### Procesos de asignación IANA

Standards Action → IESG Approval → Specification Required → Private/Experimental Use (sin registro).

---

## Relación entre ambos RFCs

RFC1035 fija la forma del mensaje en un momento dado. RFC2929 permite que esa forma evolucione (nuevos tipos, códigos) sin romper compatibilidad con implementaciones existentes. Uno define la sintaxis; el otro, la gobernanza de su extensión.

---

## Planeamiento en el DNS API

### Qué necesito parsear/construir en Java

- Voy a necesitar una clase que represente el Header, otra para Question, y otra para ResourceRecord. De ahí armo un `DnsMessage` que junte todo.
- El parser tiene que leer bytes crudos y devolver estas clases. El serializer hace lo inverso.
- Ojo con la compresión de nombres: si un servidor DNS remoto me responde con nombres comprimidos, mi parser tiene que saber seguir ese puntero. Si no lo soporto, se me rompe con respuestas reales.

### Sobre `/api/dns_resolver`

- Recibo BASE64 → decodifico a bytes → parseo con lo de arriba.
- Reviso si QR=0 y OPCODE=0, aunque en teoría el Interceptor ya filtra esto antes de mandarme el paquete "raro" directo, igual me sirve para saber qué tipo de mensaje llegó.
- Con el QNAME extraído, consulto si existe en Supabase.
  - Si existe: aplico la estrategia según el `type` del registro, esto no sale de ningún RFC, es lógica que yo tengo que programar.
  - Si no existe: mando la consulta tal cual al DNS remoto y le devuelvo lo que me responda.
- Armo la respuesta de vuelta con RDATA tipo A y la codifico en BASE64 para devolvérsela al Interceptor.

### Sobre `/api/exists`

- Más simple: solo reviso si el dominio está en la base de datos, sin tocar nada de parsing DNS.

### Por resolver

- Cómo implemento round-robin para "multi".
- Cómo calculo "menor latencia" para round-trip, probablemente usando lo que reporte el Health Checker.
- Qué pasa si el país del cliente no tiene IP asignada en "geo", el enunciado dice que ahí se retorna una IP random, tengo que programar ese fallback.
- Confirmar con el equipo del Interceptor el formato exacto que van a mandar en BASE64.

### Prompt Utilizado (Claude Sonnet 5 Low):

"Actua como tutor, necesito entender a fondo el RFC1035 y el
RFC2929, ya que forman parte del
contenido para la implementación de un servicio DNS.
Quiero que me expliques ambos RFCs desde los fundamentos
Solo genera código de ejemplo o material de estudio para asegurar que
entiendo la base teórica."
