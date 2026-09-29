# Estudio de Rust

Yo en lo personal nunca he utilizado Rust, por lo que antes de empezar a programar tomaré una sesión de teoría y práctica previa. Esta sesión fue creada por Claude. Este documento son notas tomadas por mí durante el estudio de lo que Claude generó. Al final del documento adjunto el prompt.

---
Rust es un lenguaje compilado rápido, así como C o C++. El aporte que brinda Rust es que logra seguridad de memoria en tiempo de compilación. Esto gracias a un componente llamado `borrow checker`. El compilador de Rust `rustc` es bastante estricto, de esta manera consigue eliminar bugs de tiempo de ejecución.

Rara vez se llama directamente `rustc` para compilar, usualmente se llama `cargo`, este cumple la función de gestor de proyecto, sistema de build (para compilar) y gestor de dependencias (algo así como npm). Algunos comando comunes son los siguientes:

``` r
cargo new mi_proyecto     # crea un proyecto nuevo
cargo build               # compila (modo debug)
cargo run                 # compila y ejecuta
cargo check               # verifica que compile SIN generar binario (rápido)
cargo build --release     # compila optimizado (para producción)
cargo test                # corre los tests
```
Estructura típica de un proyecto:

```r
mi_proyecto/
├── Cargo.toml        # manifiesto: nombre, versión, dependencias
├── Cargo.lock        # versiones exactas bloqueadas (autogenerado)
└── src/
    └── main.rs       # punto de entrada (para ejecutables)
```

Variables inmutables por defecto:

```r
let x = 5;
x = 6;          // ❌ ERROR de compilación: no puedes reasignar

let mut y = 5;  // 'mut' = mutable
y = 6;          // ✅ OK
```

Tipado estático y fuerte:

```r
let a = 5;          // infiere i32
let b: i64 = 5;     // tipo explícito
let c = 3.14;       // infiere f64
let d = true;       // bool
let e = 'A';        // char (un carácter Unicode, 4 bytes)
```
- Enteros con signo: i8, i16, i32, i64, i128
- Enteros sin signo: u8, u16, u32, u64, u128
- u8 es un byte — lo usarás muchísimo para paquetes de red.
- Flotantes: f32, f64

Funciones:

```r
fn suma(a: i32, b: i32) -> i32 {
    a + b          // sin 'return' ni ';' = esto es el valor devuelto
}
```
El valor de retorno se declara sin columna. Si a `a + b` se le agrega columna al final se convierte a sentencia y la función no devolvería nada.

```r
fn suma(a: i32, b: i32) -> i32 {
    return a + b;  // también válido, pero menos idiomático
}
```
### El concepto de Ownership
En Rust existen dueños de memoria. Cada variable compleja tiene un único dueño sobre su memoria. Si una variable compleja pasa su propiedad de memoria a otra variable, la variable original ya no puede acceder a esta memoria:

```r
let s1 = String::from("hola");
let s2 = s1;              // el valor se MUEVE de s1 a s2
println!("{}", s1);      // ❌ ERROR: s1 ya no es válido
```

Esto pasa únicamente con variables complejas, las variables simples que son baratas sí pueden compartir propiedad:

```r
let x = 5;
let y = x;          // se COPIA
println!("{}", x);  // ✅ OK, x sigue válido
```

Pasar un valor a una función también mueve su propiedad:

```r
fn consume(s: String) {
    println!("{}", s);
}  // aquí 's' se libera

let texto = String::from("hola");
consume(texto);
println!("{}", texto);  // ❌ ERROR: texto ya fue movido a la función
```

En caso de querer pasar una variable a una función sin perder la propiedad de esta se puede prestar la función, esto se hace pasando una referencia de la variable a la función `&`:

```r
fn calcula_longitud(s: &String) -> usize {  // toma una referencia
    s.len()
}  // 's' NO se libera aquí, porque no es el dueño

let texto = String::from("hola");
let n = calcula_longitud(&texto);  // presta 'texto'
println!("{} mide {}", texto, n);  // ✅ OK, texto sigue siendo válido
```
`&texto` es una referencia, de esta manera la función tiene acceso a la variable sin tomar la propiedad.

En caso de que la función quiera mutar la variable sin tomar propiedad entonces habría que pasarle una referencia mutable:

```r
fn agrega_mundo(s: &mut String) {
    s.push_str(" mundo");
}

let mut texto = String::from("hola");
agrega_mundo(&mut texto);
println!("{}", texto);  // "hola mundo"
```

Para evitar condiciones de carrera el compilador no permite tener varias referencias inmutables activas y junto con una referencia mutable sobre una varible:

```r
let mut s = String::from("hola");
let r1 = &s;        // ok
let r2 = &s;        // ok, varias lecturas
let r3 = &mut s;    // ❌ ERROR: no puedes tener &mut mientras hay & activos
```
### Structs y Enums
Un struct combina varios datos a la vez. Un enum representa uno entre varias posibilidades.
Los structs son parecidos a los de C:

```r
struct Paquete {
    id: u16,
    origen: String,
    tamano: usize,
}

let p = Paquete {
    id: 42,
    origen: String::from("192.168.1.1"),
    tamano: 512,
};

println!("ID: {}", p.id);
```
Con `impl` se pueden agregar métodos.
```r
impl Paquete {
    // método (toma &self = referencia a sí mismo)
    fn es_grande(&self) -> bool {
        self.tamano > 1024
    }

    // función asociada (constructor, no toma self) — como un método estático
    fn nuevo(id: u16, origen: String) -> Paquete {
        Paquete { id, origen, tamano: 0 }
    }
}

let p = Paquete::nuevo(1, String::from("10.0.0.1"));
println!("{}", p.es_grande());
```
Acá los `enums` permiten contener `structs` como campos.

```r
enum TipoRegistroDNS {
    A(String),           // dirección IPv4
    AAAA(String),        // dirección IPv6
    CNAME(String),       // alias
    MX { prioridad: u16, servidor: String },  // varios campos
}

let registro = TipoRegistroDNS::A(String::from("93.184.216.34"));
```
### Match
El `match` es como un swtich. Con el `match` se accede a los valores de un enum.
```r
match registro {
    TipoRegistroDNS::A(ip) => println!("IPv4: {}", ip),
    TipoRegistroDNS::AAAA(ip) => println!("IPv6: {}", ip),
    TipoRegistroDNS::CNAME(alias) => println!("Alias: {}", alias),
    TipoRegistroDNS::MX { prioridad, servidor } => {
        println!("MX {} (prio {})", servidor, prioridad)
    }
}
```
`_` puede ser utilizado como comodín.

```r
match numero {
    1 => println!("uno"),
    2 => println!("dos"),
    _ => println!("otro"),   // captura todo lo demás
}
```

### Option y Result
Rust no tiene `null` ni `try/catch`, en su lugar lo que se utiliza son dos enums de la librería estandar. Estos son `Result<T, E>` y `Option<T>`. Utilizar estos enums para validad posbiles errores se puede hacer un poco verboso, estos se podría agilizar con el operador `?`. La implementación específica de estos enums no me quedó muy clara, espero consolidar esta ya cuando empiece a codear.

### Colecciones importantes
`Vec<T>` permite declarar un arreglo dinámico:

```r
let mut v: Vec<u8> = Vec::new();
v.push(1);
v.push(2);
let v2 = vec![1, 2, 3];       // macro para crear rápido

for byte in &v2 {              // iterar prestando
    println!("{}", byte);
}
```
`String` vs `&str`
```r
let owned: String = String::from("hola");
let slice: &str = &owned;        // vista de la String
let literal: &str = "mundo";     // literal es &str
```
`HashMap<K,H>` permite declarar diccionarios:
```r
use std::collections::HashMap;

let mut cache: HashMap<String, String> = HashMap::new();
cache.insert(String::from("example.com"), String::from("93.184.216.34"));

if let Some(ip) = cache.get("example.com") {
    println!("En cache: {}", ip);
}
```
### Traits y genéricos
Un `trait` es el equivalente a una interfaz en otros lenguajes. En esta sección Claude también explicó los `#[derive(...)]` pero la verdad no lo entendí muy bien, espero entenderlo mejor cuando empiece a codear.

`<T>` es un tipo genérico:

```r
fn primero<T>(lista: &Vec<T>) -> &T {
    &lista[0]
}
```
### Módulos
```r
mod red {                     // define un módulo
    pub fn conectar() {}      // 'pub' = público, accesible desde afuera
    fn interno() {}           // privado por defecto
}

fn main() {
    red::conectar();          // acceso con ::
}
```

---
#### Fuentes
Claude Opus 4.8,
*Prompt: En este proyecto tendré que trabajar con el lenguaje Rust. Nunca he trabajado con este lenguaje. Quiero que hagamos una sesión de aprendizaje pre-programación en Rust. Explicame cómo funciona este lenguaje  (sesión de teoría) y luego pasamos a una sesión de entrenamiento (sesión práctica). Empecemos con la sesión de teoría.*