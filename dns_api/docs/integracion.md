# Apuntes — Supabase, JDBC y Spring Data JPA

## 1. Por qué estas 3 juntas

Estas 3 tecnologías permiten un backend en Java que se pueda guardar datos en una base relacional que vive en la nube. Supabase es quien guarda los datos. JDBC es cómo Java habla con bases de datos relacionales en general. Spring Data JPA es la capa que me ahorra escribir SQL a mano dentro de Spring.

---

## 2. Supabase

### Qué trae por dentro

- **PostgreSQL administrado**. Es un Postgres real, con todo lo que eso implica: SQL completo, relaciones, transacciones, vistas, funciones.
- **PostgREST**. Convierte cada tabla en un endpoint REST automáticamente, sin escribir backend para eso.
- **GoTrue**. Sistema de autenticación ya incluido.
- **Realtime**. Permite suscribirse a cambios en la base en tiempo real, por websockets.
- **Storage**. Guarda archivos binarios.

### Dos formas de llegar a los datos

Como el fondo es Postgres puro, hay dos caminos distintos para tocar los mismos datos.

Uno es conectarse directo a la base con el protocolo nativo de Postgres. En Java esto es una conexión JDBC normal.

El otro es usar la API REST que se genera sola (PostgREST). Esta forma nació pensando en clientes livianos como un navegador o una app móvil, para no tener que exponer credenciales de base de datos ahí.

Esta doble opción es justo la razón por la que, al conectar Supabase con una API en Java ya existente, hay que decidir cuál camino tomar.

### Modos de conexión (pooling)

Supabase ofrece varias formas de manejar las conexiones según el tipo de carga que tenga la aplicación.

**Conexión directa.** Una conexión persistente uno a uno. Sirve para procesos largos con pocas conexiones simultáneas.

**Session Pooler.** Un pool que se comporta como una sesión completa de Postgres. Soporta cosas como prepared statements guardados del lado del servidor.

**Transaction Pooler.** Pensado para muchas conexiones cortas y de alta concurrencia, típico en entornos serverless. Tiene limitaciones. No soporta funciones que dependen de mantener estado de sesión.

Esta diferencia importa mucho para cualquier cliente que necesite prepared statements de sesión.

---

## 3. JDBC

### Qué es

JDBC es la API estándar de Java para conectarse a bases de datos relacionales. Define interfaces como `Connection`, `Statement`, `PreparedStatement` y `ResultSet`. Cada proveedor de base de datos implementa esas interfaces con su propio driver JDBC.

Es la capa más baja del acceso a datos en Java. Todo lo demás, JPA, Hibernate, Spring Data, se construye encima de JDBC.

### Piezas centrales

**Driver.** Traduce las llamadas genéricas de JDBC al protocolo real de esa base de datos específica.

**Connection String.** Tiene el formato `jdbc:<subprotocolo>://<host>:<puerto>/<basededatos>?parametros`. Dice dónde y cómo conectarse.

**Statement vs PreparedStatement.** `Statement` ejecuta SQL directo. `PreparedStatement` precompila la consulta y acepta parámetros. Esto protege contra inyección SQL y mejora el rendimiento cuando se repite la misma consulta muchas veces.

**Prepared statements del lado del servidor.** El servidor guarda el plan de ejecución de una consulta parametrizada para reutilizarlo después. Es una optimización real, pero necesita que la conexión mantenga estado de sesión. No todos los modos de pooling lo permiten.

### Seguridad en la cadena de conexión

El parámetro `sslmode` decide si la conexión va cifrada. Con `require` se obliga TLS. Con `prefer`, que suele venir por defecto, intenta cifrar pero si falla cae a una conexión sin cifrar sin avisar. Es fácil pasarlo por alto y tiene implicaciones reales si la cadena de conexión lleva credenciales.

Los caracteres especiales en la contraseña, como `&`, `#`, `?` o espacios, hay que codificarlos con percent-encoding dentro de la URL. Si no, el parser de la URL los confunde con separadores de parámetros.

Las credenciales nunca deben quedar en texto plano en el código ni en archivos que se suben al repositorio. Lo correcto es inyectarlas por variable de entorno en tiempo de ejecución.

---

## 4. Spring Data JPA

### Qué es

JPA es la especificación estándar de Java para mapear objetos a tablas relacionales. Permite representar una tabla como una clase Java marcada con `@Entity`, y cada fila como una instancia de esa clase, sin escribir SQL para lo básico. Hibernate es la implementación de JPA que más se usa en la práctica.

Spring Data JPA agrega una capa más encima de JPA/Hibernate. Con solo declarar una interfaz que extienda `JpaRepository` ya se tienen las operaciones CRUD funcionando, sin implementar nada.

### Cómo se acomodan las capas

Aplicación (Service Layer)
↓
Spring Data JPA genera la implementación del repositorio automáticamente
↓
Hibernate (JPA) traduce operaciones sobre entidades a SQL
↓
JDBC ejecuta ese SQL contra la base usando el driver correspondiente
↓
Base de datos (PostgreSQL, MySQL, etc.)

Cada capa depende de la de abajo, pero le esconde la complejidad a la de arriba. Quien usa Spring Data JPA normalmente no escribe SQL ni maneja un `Connection` a mano.

### Conceptos que tengo que tener claros

`@Entity` marca una clase como mapeada a una tabla.

`@Id` marca el campo que es la llave primaria.

`JpaRepository<T, ID>` es la interfaz que, al extenderla, ya trae `save()`, `findById()`, `findAll()`, `deleteById()` sin escribir nada más.

Las consultas derivadas se generan solas a partir del nombre del método. Por ejemplo `findByDomain(String domain)` se convierte en `WHERE domain = ?` sin escribir SQL.

`@Query` sirve para cuando la consulta derivada no alcanza. Se puede escribir JPQL (parecido a SQL pero sobre entidades) o SQL nativo.

### Cosas a tener en cuenta al conectar con un proveedor externo

Hibernate por defecto usa prepared statements del lado del servidor para rendir mejor. Esto significa que el modo de pooling que use el proveedor de base de datos tiene que soportar eso. Si no lo soporta, aparecen errores intermitentes difíciles de explicar, porque solo fallan bajo ciertas condiciones de concurrencia.

El connection pool de la aplicación (HikariCP, que ya viene por defecto en Spring Boot) es un nivel de pooling aparte del que ofrece el proveedor de base de datos. Ambos niveles hay que configurarlos de forma coherente entre sí.

El resto de la configuración, URL, usuario, contraseña, se pone en las propiedades estándar de Spring Boot (`spring.datasource.*`), normalmente resueltas desde variables de entorno para no exponer credenciales.

---

## 5. Cómo se conectan los tres

Supabase administra la base real de PostgreSQL y decide qué formas de acceso ofrece.

JDBC es el protocolo de bajo nivel que permite a una app en Java hablar con esa base relacional, sin importar quién la hospede.

Spring Data JPA es la capa de productividad construida sobre JDBC, a través de Hibernate, que permite trabajar con objetos Java y repositorios en vez de conexiones y SQL manual.

Juntas forman una ruta de acceso a datos completamente estándar dentro de Java y Spring, que funciona igual con cualquier PostgreSQL, Supabase incluido, sin necesitar librerías especiales del proveedor.

---

## 6. Cómo pienso aplicar esto en mi componente

Esto es lo que estoy pensando hacer, todavía no es definitivo.

Voy a usar la opción A que decidí antes, JDBC directo con Spring Data JPA, en vez de llamar a la API REST de PostgREST a mano o usar el SDK no oficial de Java para Supabase.

La interfaz `DnsRecordRepository` que ya tengo definida en la arquitectura no cambia. Por dentro, su implementación va a apoyarse en una interfaz que extienda `JpaRepository`, y yo solo tengo que mapear la entidad `DnsRecordEntity` a la tabla real que se defina.

Tengo que acordarme de usar el Session Pooler en el puerto 5432, no el de transacciones en el 6543, porque Hibernate necesita los prepared statements de sesión.

También tengo que poner `sslmode=require` a mano en la URL de conexión, porque si no el driver puede caer en una conexión sin cifrar sin que yo me dé cuenta.

Las credenciales de Supabase van todas por variable de entorno, nunca las voy a dejar escritas en el `application.yml` ni subidas al repositorio.

### Por resolver

Todavía no tengo el proyecto de Supabase creado, entonces no tengo la URL de conexión real ni las credenciales.

Falta coordinar con el equipo de DNS UI el esquema exacto de las tablas, porque ellos son los que diseñan la base de datos según el enunciado.

Falta escribir la entidad `DnsRecordEntity` y la clase que realmente implemente `DnsRecordRepository` usando JPA.

Falta probar que la conexión funcione de verdad desde el contenedor Docker hacia Supabase.

### Prompt utilizado (Claude Sonnet 5, Low):

"Necesito entender Supabase, JDBC y Spring Data JPA, tengo que conectar una API en Java a una base de datos en Supabase. Explícame cada tecnología desde lo básico y cómo se relacionan entre sí. También ayúdame a comparar las formas de conectar Java con Supabase (JDBC directo, REST vía PostgREST, o algún SDK) para poder decidir cuál me conviene usar."
