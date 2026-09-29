# Uso de IA: prototipo de DNS UI con Stitch

## Alcance

Este documento deja constancia de cómo se usó Stitch para crear la primera
propuesta visual de la interfaz.

Primero se preparó un prompt breve a partir de los requisitos del proyecto. Luego
se refinó y amplió con ChatGPT antes de enviarlo a Stitch.

## Prompt original

Diseña una UI web sencilla en español para DNS UI.

Debe permitir crear, editar y eliminar registros DNS de tipo single, multi, weight, round-trip y geo. También debe permitir agregar health checks TCP o HTTP a cada registro.

Incluye otra pantalla llamada IP to Country para administrar registros con IP y país.

Usa un diseño limpio tipo panel administrativo. No agregues login, métricas, gráficas ni funciones extra.

## Prompt enviado a Stitch

Diseña un prototipo web de alta fidelidad para una aplicación llamada “DNS UI”. Debe ser una interfaz simple, clara y profesional para administrar registros DNS y sus health checks. El idioma completo de la interfaz debe ser español.

Es un prototipo visual de frontend: no incluir conexión con API, base de datos, autenticación, métricas, gráficas ni configuraciones técnicas no solicitadas. Debe ser fácil de implementar después en Next.js/React, sin animaciones llamativas ni componentes innecesarios.

La interfaz debe tener una navegación lateral pequeña con dos secciones:

1. Registros DNS
2. IP to Country

La vista inicial debe ser “Registros DNS”.

### Vista: Registros DNS

Mostrar:

- Título “Registros DNS”.
- Botón principal “Crear registro”.
- Una tabla o lista limpia de registros existentes.
- Cada registro debe mostrar: nombre del dominio, tipo de registro y acciones “Editar” y “Eliminar”.
- Usar ejemplos visuales simples, sin mostrar datos técnicos que no estén definidos.
- Los cinco tipos de registro deben aparecer exactamente así: `single`, `multi`, `weight`, `round-trip` y `geo`.

Al seleccionar “Crear registro” o “Editar”, abrir un panel lateral o modal con:

- Campo “Nombre del dominio”.
- Selector visual para elegir el tipo: `single`, `multi`, `weight`, `round-trip` o `geo`.
- La configuración debe cambiar según el tipo seleccionado:
   - `single`: una dirección IP.
   - `multi`: una lista de direcciones IP con acción “Agregar IP”.
   - `weight`: una lista de direcciones IP, cada una con su peso asociado.
   - `round-trip`: una lista de direcciones IP que podrán asociarse a health checks.
  - `geo`: una lista de asignaciones de país y dirección IP.

No incluir TTL, puertos, subdominios, proveedores DNS, logs, dashboards, ni campos adicionales no solicitados.

Dentro del mismo panel del registro, incluir una sección clara llamada “Health checks asociados”, con un botón “Agregar health check”.

### Configuración de health checks

Al agregar un health check, mostrar un formulario con selector entre `TCP` y `HTTP`.

Para health check TCP, mostrar únicamente:

- Timeout
- Retries
- Intervalo entre pruebas

Para health check HTTP, mostrar únicamente:

- Path
- Timeout
- Retries
- Intervalo entre pruebas
- Códigos HTTP esperados

Usar etiquetas claras y campos visualmente ordenados. No agregar campos de autenticación básica, puertos, URL completa, headers ni otros datos que no están definidos para el DNS UI.

### Vista: IP to Country

Crear una segunda pantalla accesible desde la navegación lateral.

Mostrar:

- Título “IP to Country”.
- Botón “Agregar registro”.
- Tabla con las columnas “IP”, “País” y “Acciones”.
- Acciones “Editar” y “Eliminar”.
- Modal o panel para crear y editar un registro con los campos “IP” y “País”.

No incluir ciudad, coordenadas, rangos IP, proveedor, continente ni campos adicionales.

### Estilo visual

Usar un diseño de panel administrativo sobrio y moderno:

- Fondo claro, tarjetas blancas y bordes suaves.
- Color principal azul oscuro o azul medio.
- Tipografía legible y jerarquía visual clara.
- Botones de acción visibles pero discretos.
- Iconos simples para crear, editar, eliminar y navegar.
- Diseño desktop primero, pero adaptable a pantallas pequeñas.
- Priorizar claridad, espacio en blanco y facilidad de uso sobre decoración.

La interfaz debe verse como una herramienta académica funcional y sencilla, no como un dashboard empresarial con estadísticas.

## Aspectos encontrados al implementar el prototipo

El prototipo cumplía el objetivo de definir la apariencia de la aplicación. Sin
embargo, al conectarlo con la API aparecieron algunas diferencias entre lo que se
había diseñado y la información que el sistema realmente necesita guardar.

| Situación | Efecto al implementar | Decisión actual |
| --- | --- | --- |
| En el prototipo, IP to Country solo manejaba una IP y un país. | La API trabaja con rangos de IP y también puede guardar el nombre del país, ciudad y coordenadas. | La pantalla se ajustó a esos datos para que la información de la UI no se pierda al guardarla. |
| El prototipo no incluía TTL. | La API necesita ese valor para crear y editar un registro DNS correctamente. | Se agregó el campo TTL en la UI. |
| Para `round-trip`, el prototipo solo pedía direcciones IP. | La API también necesita latitud y longitud para cada dirección. | La UI solicita esos datos solo cuando se selecciona `round-trip`. |
| El prototipo permitía agregar health checks asociados. | La versión actual usa una misma configuración de health check para los targets del registro; todavía no permite definir varios checks independientes para una misma IP. | Se mantiene esta forma de trabajo porque es la que soporta la integración actual. Si el requisito final exige varios checks por IP, habrá que ampliarlo. |

## Conclusión

Stitch se utilizó como apoyo para definir una interfaz inicial clara y sencilla.
