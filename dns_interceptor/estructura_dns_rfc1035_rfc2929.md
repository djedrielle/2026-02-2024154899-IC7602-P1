# Estructura de mensajes DNS y políticas (RFC 1035 + RFC 2929)

> Referencia para implementar el parseo/serialización y reescritura de paquetes DNS
> en el interceptor. Basado en:
> - **RFC 1035** — https://datatracker.ietf.org/doc/html/rfc1035
> - **RFC 2929** (BCP 42) — https://datatracker.ietf.org/doc/html/rfc2929

## Qué aporta cada RFC

- **RFC 1035** define el **formato concreto en el cable** (*wire format*): cómo se
  acomodan los bytes, la codificación de nombres, la compresión, los tipos de
  registro, los límites y el transporte. Es la referencia para *serializar/parsear*.
- **RFC 2929** (BCP 42) **no cambia el formato**: define las **políticas de
  asignación** de los valores de cada campo (qué códigos son válidos, cuáles
  reservados, cuáles de uso futuro, y quién puede asignarlos). Además **consolida y
  clarifica** el significado de los bits del header (incluye los bits `AD` y `CD` que
  1035 no tenía). Es la referencia para saber *qué valores son legales y qué hacer con
  los desconocidos*.

> **Nota:** RFC 2929 fue actualizado por RFC 5395, luego RFC 6195, y hoy el vigente es
> **RFC 6895** (misma serie BCP 42). Las políticas de asignación se han relajado con el
> tiempo. Para esta tarea 2929 es correcto; si algún valor parece "más nuevo", esa es
> la razón.

---

## 1. Estructura general del mensaje (RFC 1035 §4.1)

Todo mensaje DNS —consulta o respuesta— tiene **las mismas 5 secciones**, en este orden:

```
+---------------------+
|        Header       |  12 bytes, tamaño fijo — SIEMPRE presente
+---------------------+
|       Question      |  las preguntas (QDCOUNT entradas)
+---------------------+
|        Answer       |  RRs que responden (ANCOUNT)
+---------------------+
|      Authority      |  RRs de servidores autoritativos (NSCOUNT)
+---------------------+
|      Additional     |  RRs adicionales (ARCOUNT)
+---------------------+
```

Los `*COUNT` del header indican **cuántas entradas** hay en cada sección: se lee el
header y de ahí se sabe cuántas preguntas y cuántos RRs leer.

**Todos los enteros multi-byte van en *big-endian*** (network byte order, byte más
significativo primero).

---

## 2. Header (12 bytes) — RFC 1035 §4.1.1 + RFC 2929 §2

Layout de bits (cada fila = 16 bits = 2 bytes):

```
                                    1  1  1  1  1  1
      0  1  2  3  4  5  6  7  8  9  0  1  2  3  4  5
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                      ID                       |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |QR|   Opcode  |AA|TC|RD|RA| Z|AD|CD|   RCODE   |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                    QDCOUNT                     |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                    ANCOUNT                     |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                    NSCOUNT                     |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                    ARCOUNT                     |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
```

La segunda fila (bytes 2 y 3) son **campos de bits empaquetados**. De izquierda a derecha:

| Campo      | Bits | Significado |
|------------|------|-------------|
| **ID**     | 16   | Identificador de la transacción. El cliente lo elige; el servidor **lo copia tal cual** en la respuesta para emparejar consulta↔respuesta. |
| **QR**     | 1    | 0 = consulta (query), 1 = respuesta (response). |
| **Opcode** | 4    | Tipo de operación (ver §6). |
| **AA**     | 1    | *Authoritative Answer*: el servidor es autoritativo para el nombre. Solo en respuestas. |
| **TC**     | 1    | *TrunCation*: el mensaje se truncó porque no cabía (típicamente >512 en UDP). |
| **RD**     | 1    | *Recursion Desired*: el cliente pide recursión. Se **copia** de la consulta a la respuesta. |
| **RA**     | 1    | *Recursion Available*: el servidor indica que soporta recursión. |
| **Z**      | 1    | Reservado. **Debe ser 0** en consultas y respuestas (la "spare bit" de RFC 2929 §2.1). |
| **AD**     | 1    | *Authentic Data* (DNSSEC). Añadido/clarificado por RFC 2929. |
| **CD**     | 1    | *Checking Disabled* (DNSSEC). Añadido/clarificado por RFC 2929. |
| **RCODE**  | 4    | Código de respuesta (ver §6). |

> RFC 1035 original tenía un `Z` de **3 bits** reservados. DNSSEC (y RFC 2929) reasignó
> dos de esos bits a `AD` y `CD`. El layout correcto hoy es `Z(1) AD(1) CD(1)`.

Contadores (16 bits cada uno):

| Campo        | Cuenta las entradas de… |
|--------------|-------------------------|
| **QDCOUNT**  | sección Question |
| **ANCOUNT**  | sección Answer |
| **NSCOUNT**  | sección Authority |
| **ARCOUNT**  | sección Additional |

---

## 3. Sección Question (RFC 1035 §4.1.2)

Se repite `QDCOUNT` veces (casi siempre 1). Cada entrada:

```
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    /                     QNAME                     /   <- longitud variable
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                     QTYPE                      |   <- 16 bits
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                     QCLASS                     |   <- 16 bits
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
```

- **QNAME**: el nombre de dominio consultado, codificado como labels (ver §5).
- **QTYPE**: tipo consultado. Es un **superconjunto** de los TYPE: incluye los tipos
  normales *más* los "QTYPE-only" como `AXFR` (252), `MAILB` (253), `MAILA` (254),
  `*`/ANY (255).
- **QCLASS**: clase consultada; superconjunto de CLASS, incluye `*`/ANY (255).

---

## 4. Resource Record — formato común (RFC 1035 §3.2.1, §4.1.3)

**Answer, Authority y Additional** usan **el mismo formato de RR**. Cada RR:

```
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    /                      NAME                     /   <- nombre (variable)
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                      TYPE                      |   <- 16 bits
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                     CLASS                     |   <- 16 bits
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                      TTL                      |   <- 32 bits
    |                                               |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                   RDLENGTH                     |   <- 16 bits
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    /                     RDATA                      /   <- RDLENGTH bytes
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
```

| Campo        | Tamaño       | Nota |
|--------------|--------------|------|
| **NAME**     | variable     | El dominio al que pertenece el RR. |
| **TYPE**     | 16 bits      | Tipo del registro (A, NS, CNAME…). |
| **CLASS**    | 16 bits      | Normalmente `IN` (1). |
| **TTL**      | 32 bits      | Segundos que puede cachearse. Interpretado como **no-negativo** (§2.3.4). `0` = no cachear. |
| **RDLENGTH** | 16 bits      | Longitud en bytes de RDATA. **Clave para el parser**: dice cuánto leer aunque no se conozca el tipo. |
| **RDATA**    | RDLENGTH bytes | Contenido; su formato **depende de TYPE y CLASS**. |

El diseño con `RDLENGTH` es intencional: aunque no sepas parsear un tipo, siempre puedes
**saltarlo** leyendo `RDLENGTH` bytes.

### Formatos de RDATA para los tipos más comunes (RFC 1035 §3.3–§3.4)

- **A** (1) — §3.4.1: dirección IPv4 = **4 bytes** crudos.
- **NS** (2) — §3.3.11: `NSDNAME` = un nombre de dominio.
- **CNAME** (5) — §3.3.1: `CNAME` = un nombre de dominio (el nombre canónico).
- **SOA** (6) — §3.3.13: `MNAME` (nombre), `RNAME` (nombre), y luego 5 enteros de 32 bits:
  `SERIAL`, `REFRESH`, `RETRY`, `EXPIRE`, `MINIMUM`.
- **PTR** (12) — §3.3.12: `PTRDNAME` = un nombre de dominio (para DNS inverso).
- **MX** (15) — §3.3.9: `PREFERENCE` (16 bits) + `EXCHANGE` (nombre de dominio).
- **TXT** (16) — §3.3.14: una o más *character-strings*.
- **HINFO** (13) — §3.3.2: dos *character-strings* (`CPU` y `OS`).

> **Dos codificaciones de "cadena" distintas:**
> - **Domain name**: secuencia de labels con longitud + terminador cero (puede usar compresión).
> - **character-string**: **1 byte de longitud** (0–255) seguido de esos bytes. *No* usa
>   compresión. Aparece en TXT, HINFO, etc.

---

## 5. Codificación de nombres y COMPRESIÓN (RFC 1035 §3.1, §4.1.4)

Es lo que **más errores causa** al parsear y donde hay implicaciones de seguridad.

### Nombre normal (sin comprimir)

Un nombre es una **secuencia de labels**. Cada label es:

```
[ 1 byte de longitud N ][ N bytes del texto del label ]
```

y el nombre **termina** con un byte de longitud **cero** (el label raíz `.`).
Ejemplo, `www.example.com`:

```
3 'w' 'w' 'w'  7 'e' 'x' 'a' 'm' 'p' 'l' 'e'  3 'c' 'o' 'm'  0
```

### Compresión por punteros

Un nombre puede terminar con un **puntero** a otro punto del mensaje. El truco está en
**los dos bits altos del byte de longitud**:

```
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    | 1  1|              OFFSET (14 bits)           |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
```

| Bits altos | Significado |
|------------|-------------|
| `00`       | Label normal; los otros 6 bits son la longitud (0–63). |
| `11`       | **Puntero de compresión** (byte ≥ `0xC0`). Los 14 bits restantes son un **OFFSET** desde el inicio del mensaje (byte 0 = el ID) donde continúa el nombre. |
| `01`, `10` | **Reservados** para uso futuro. No generarlos. |

Un nombre puede ser:
1. una secuencia de labels terminada en `0`, o
2. un puntero, o
3. una secuencia de labels que **termina en un puntero**.

El OFFSET del puntero es **absoluto desde el inicio del mensaje completo**; el parser
necesita acceso al buffer entero, no solo a la porción actual.

### ⚠️ Políticas/seguridad de compresión (crítico para un interceptor)

- **Al parsear, DEBES soportar compresión** o fallarás con respuestas reales.
- **Punteros solo hacia atrás**: un puntero debe apuntar a una posición *anterior* a sí
  mismo. Un puntero hacia adelante o a sí mismo puede crear un **bucle infinito** → el
  parser debe detectarlo (límite de saltos, o exigir offset decreciente). Vector clásico de DoS.
- Valida que el OFFSET caiga **dentro** del mensaje.
- **Al reescribir/reserializar**: lo más seguro es **descomprimir** los nombres al
  parsear y, al emitir, o bien no comprimir, o comprimir con cuidado. Si cambias bytes
  del mensaje, **todos los offsets de los punteros existentes se invalidan**. Para un
  interceptor, reconstruir el mensaje desde una representación en memoria suele ser más
  seguro que editar bytes in-place.

---

## 6. Valores y políticas de asignación (RFC 2929)

### Opcode (4 bits) — RFC 2929 §2.2

| Valor | Nombre | Origen |
|-------|--------|--------|
| 0     | **QUERY** (consulta estándar) | RFC 1035 |
| 1     | IQUERY (inversa) | 1035 — **obsoleto** por RFC 3425 |
| 2     | STATUS | RFC 1035 |
| 3     | (sin asignar) | |
| 4     | NOTIFY | RFC 1996 |
| 5     | UPDATE | RFC 2136 |
| 6–15  | disponibles | asignación por *IETF Standards Action* |

### RCODE — RFC 2929 §2.3

Son 4 bits **en el header** (0–15), pero el espacio está **extendido** por EDNS0 (el OPT
RR aporta 8 bits altos → RCODE de 12 bits) y por TSIG/TKEY (campo de error de 16 bits).
Valores >15 **no caben** en el header solo:

| Valor | Nombre    | Significado |
|-------|-----------|-------------|
| 0     | NoError   | sin error |
| 1     | FormErr   | error de formato en la consulta |
| 2     | ServFail  | fallo del servidor |
| 3     | NXDomain  | el nombre no existe |
| 4     | NotImp    | no implementado |
| 5     | Refused   | rechazado (política) |
| 6     | YXDomain  | (RFC 2136, Update) |
| 7     | YXRRSet   | (RFC 2136) |
| 8     | NXRRSet   | (RFC 2136) |
| 9     | NotAuth   | no autoritativo (RFC 2136) |
| 10    | NotZone   | (RFC 2136) |
| 16+   | BADVERS/BADSIG, BADKEY, BADTIME… | solo vía EDNS0/TSIG, no en header |

Los primeros 6 (0–5) son los de RFC 1035; el resto viene de extensiones y 2929 los consolida.

### TYPE / QTYPE (16 bits) — RFC 2929 §3.1

Tipos base de RFC 1035 §3.2.2:

| #  | Tipo              | #  | Tipo        |
|----|-------------------|----|-------------|
| 1  | **A** (IPv4)      | 10 | NULL (exp.) |
| 2  | **NS**            | 11 | WKS         |
| 3  | MD (obsoleto→MX)  | 12 | **PTR**     |
| 4  | MF (obsoleto→MX)  | 13 | HINFO       |
| 5  | **CNAME**         | 14 | MINFO       |
| 6  | **SOA**           | 15 | **MX**      |
| 7  | MB (exp.)         | 16 | **TXT**     |
| 8  | MG (exp.)         |    |             |
| 9  | MR (exp.)         |    |             |

Solo en QTYPE (consultas): `AXFR`=252, `MAILB`=253, `MAILA`=254 (obsoleto), `*`/ANY=255.
Fuera de 1035 pero relevante: `AAAA`=28 (IPv6), `OPT`=41 (pseudo-RR de EDNS0). RFC 2929
marca `OPT` como *meta-tipo* que no se cachea ni aparece en zonas.

Política 2929: nuevas asignaciones de TYPE requerían *IETF Standards Action* (relajado
después por 5395/6195 a *expert review* con plantilla).

### CLASS / QCLASS (16 bits) — RFC 2929 §3.2

| Valor        | Nombre     | Nota |
|--------------|------------|------|
| 0            | reservado  | |
| 1            | **IN**     | Internet (el que se usa casi siempre) |
| 2            | (disponible) | fue CS/CSNET, obsoleto |
| 3            | CH         | Chaos |
| 4            | HS         | Hesiod |
| 254          | NONE       | solo QCLASS (RFC 2136) |
| 255          | `*` / ANY  | solo QCLASS |
| 65280–65534  | uso privado | |
| 65535        | reservado  | |

### Regla general de valores reservados (RFC 2929)

- El valor **0** y el **65535** suelen quedar **reservados** en los espacios de 16 bits.
- Los rangos de "uso privado" existen para experimentación local.
- **No generes** valores reservados ni de "uso futuro" (`01`/`10` en punteros, bit `Z`,
  opcodes/rcodes sin asignar).

---

## 7. Límites y transporte (RFC 1035 §2.3.4, §4.2)

Reglas duras que la implementación debe respetar:

- **Label**: ≤ **63 bytes** (los 2 bits altos se usan para compresión → solo 6 bits de longitud).
- **Nombre completo**: ≤ **255 bytes** (incluyendo los bytes de longitud).
- **character-string**: ≤ 255 bytes (longitud en 1 byte).
- **TTL**: 32 bits, tratado como no-negativo.
- **UDP (puerto 53)**: mensaje ≤ **512 bytes**. Si no cabe → se **trunca** y se pone el bit
  **`TC`**; el cliente debe **reintentar por TCP**. (Este límite lo amplía EDNS0/RFC 6891.)
- **TCP (puerto 53)**: el mensaje va **precedido por un campo de longitud de 2 bytes**
  (big-endian). **No olvidar este prefijo** al pasar de UDP a TCP: es el error #1.
- **Case-insensitive** (§2.3.3): la comparación de nombres **ignora mayúsculas/minúsculas**,
  pero se debe **preservar** el case original cuando se pueda.

---

## 8. Políticas clave específicas para un INTERCEPTOR

Lo que más importa al interceptar/reescribir tráfico DNS, derivado de ambos RFC:

1. **Emparejamiento por ID**: copia el `ID` de la consulta a la respuesta sin cambiarlo.
2. **Eco de la sección Question**: una respuesta normalmente **repite la(s) pregunta(s)**
   original(es), con `QDCOUNT` igual. Los resolvers validan que la pregunta coincida
   (defensa anti-spoofing). Al interceptar, **mantén la pregunta consistente**.
3. **Coherencia de contadores**: `QDCOUNT/ANCOUNT/NSCOUNT/ARCOUNT` deben coincidir
   **exactamente** con el número real de entradas. Si agregas o quitas RRs, **actualiza
   los contadores**.
4. **Bit `QR`**: 0 en consultas, 1 en respuestas. Si fabricas respuestas, ponlo en 1.
5. **`RD`/`RA` con sentido**: copia `RD` de la consulta; pon `RA` según si el interceptor
   ofrece recursión.
6. **Bits reservados = 0**: `Z` en 0. No toques `AD`/`CD` salvo que hagas DNSSEC.
7. **Tipos/clases desconocidos**: usa `RDLENGTH` para **saltar** RRs que no entiendas, en
   vez de fallar. No asumas que solo verás A/AAAA.
8. **Compresión al reescribir**: si modificas el mensaje, **los offsets de compresión
   cambian**. Reconstruir desde una estructura en memoria es más seguro que editar bytes.
   **Defiéndete de bucles de punteros** al parsear (límite de saltos + validar offsets).
9. **Truncación y TCP**: si la respuesta reescrita supera 512 bytes en UDP, marca `TC` (o
   soporta TCP con su prefijo de 2 bytes). No envíes mensajes UDP sobredimensionados.
10. **Big-endian siempre** y **valida límites** (63/255/512) al leer y al escribir; no
    confíes en que el tráfico entrante los respete (entrada hostil).

---

## Orden sugerido de implementación

1. Parsear/serializar el **header** (12 bytes fijos, lo más simple para empezar).
2. Codificación/decodificación de **nombres** *sin* compresión.
3. **Question** completa.
4. **RR** genérico usando `RDLENGTH`.
5. Añadir **soporte de compresión** al leer nombres (con protección de bucles).
6. RDATA específico por tipo (A, AAAA, CNAME, MX…).
