# Informe del Sistema de Tickets y Boletas de Pago — ParkUTP

> Documento técnico de la implementación del **sistema de tickets de estancia y boleta de pago
> descargable**, y de la **inicialización automática de la base de datos** al arrancar el backend.
>
> | | |
> |---|---|
> | **Proyecto** | ParkUTP — sistema de gestión de estacionamientos |
> | **Backend** | Java 17+ · `com.sun.net.httpserver` · JDBC PostgreSQL · Jackson |
> | **Frontend** | HTML / CSS / JS estático servido por el propio backend |
> | **Base de datos** | PostgreSQL |
> | **Estado** | Implementado, compilado y verificado de extremo a extremo |

---

## 1. Resumen ejecutivo

Se implementaron dos funcionalidades sobre ParkUTP:

1. **Sistema de tickets de estancia y boleta de pago descargable.** El cliente calcula lo que debe
   por su estancia, emite una boleta que queda registrada en la base de datos, y puede llevársela
   en **texto plano (`.txt`)** o por **impresión web**.
2. **Inicialización automática de la base de datos.** Al ejecutar `HttpServerApp.main()`, el backend
   crea la base si no existe y aplica `schema.sql` y `seed.sql` con sentencias **idempotentes**
   (`CREATE TABLE IF NOT EXISTS`), de modo que arrancar el servidor sobre una base vacía o ya
   inicializada funciona sin intervención manual.

Consecuencia práctica: en una máquina limpia basta con tener PostgreSQL levantado y ejecutar el
backend; la base de datos se crea y queda lista para usar.

---

## 2. Descripción general de las funcionalidades

### 2.1 Tickets de estancia y boleta de pago

**Qué es un ticket.** Es el registro persistente de una estancia cerrada. Cuando el cliente retira
su vehículo, se emite un `ticket` que congela los datos de la reserva (placa, vehículo, espacio,
parking), los dos instantes de la estancia (entrada y salida) y el desglose del importe.

**Ciclo de vida en la interfaz del cliente (`frontend/usuario.html`):**

```
   Reserva ACTIVA
        │
        │  pulsas "Emitir boleta"
        ▼
   GET /api/tickets/estimado ──► modal de confirmación
   (tiempo de estancia,          (estancia / facturable / tarifa / total)
    facturable, tarifa, total)         │
        │  pulsas "Emitir boleta"       ▼
        ▼                        POST /api/tickets
   Reserva FINALIZADA  ◄───────── (transacción: inserta ticket,
   espacio liberado                 cierra reserva, libera espacio)
        │
        ▼
   Modal con la boleta:  [Ver]  [Descargar .txt]  [Imprimir]
   + sección "Mis Boletas" con el historial del cliente
```

**Características de la implementación:**

- **Cálculo en dos tiempos.** El endpoint de *estimado* no escribe nada en la base de datos: solo
  calcula y devuelve el desglose para que el cliente confirme. El endpoint de emisión vuelve a
  calcularlo dentro de la transacción y lo persiste.
- **Una sola boleta por reserva.** La tabla `tickets` tiene `UNIQUE(reservation_id)` y además la
  emisión comprueba el estado `ACTIVA` de la reserva dentro de la misma transacción.
- **Emisión transaccional y serializada.** La reserva se bloquea con `SELECT … FOR UPDATE`, de modo
  que dos peticiones simultáneas sobre la misma reserva no pueden emitir dos boletas ni liberar dos
  veces el mismo espacio.
- **Boleta descargable en texto plano.** Formato de ticket de 40 columnas, el habitual de los
  parquímetros, listo para el Bloc de notas de Windows (se descarga con BOM UTF-8).
- **Impresión web.** La boleta se imprime desde un `iframe` oculto con su propio documento HTML y
  sus reglas `@media print`, sin abrir ventanas emergentes ni ser bloqueado por el navegador.
- **Sin inyección de HTML.** Todos los datos que vienen del servidor (la placa, el nombre del
  parking…) se escapan con `escaparHtml()` antes de insertarse en el DOM, y los manejadores de
  evento se delegan con `data-index` en vez de `onclick` interpolado.

### 2.2 Inicialización automática de la base de datos

Al arrancar, `HttpServerApp.main()` invoca `DatabaseInitializer.inicializar()` **antes** de levantar
el servidor HTTP. El inicializador:

1. **Localiza la carpeta de scripts SQL** probando, en orden: el valor de `-Dparkutp.sql.dir` o la
   variable `PARKUTP_SQL_DIR`, `backend/sql/`, `sql/`, `../sql/`, `../backend/sql/` y, como último
   recurso, la ruta relativa a las clases compiladas (`backend/target/classes → backend/sql`).
   Así funciona tanto desde la raíz del proyecto como desde `backend/`.
2. **Comprueba la base de datos.** Si la conexión falla porque la base no existe, se conecta a
   `postgres` y la crea (`CREATE DATABASE`).
3. **Aplica `schema.sql`.** Divide el script en sentencias y convierte cada `CREATE TABLE` en
   `CREATE TABLE IF NOT EXISTS` (igual para `CREATE INDEX`, `CREATE UNIQUE INDEX`, `CREATE SCHEMA`
   y `CREATE SEQUENCE`). Así, una base ya inicializada no genera errores.
4. **Aplica `seed.sql`** con los usuarios y parkings de prueba. Sus sentencias ya llevan
   `ON CONFLICT`, por lo que son idempotentes por sí solas y no duplican filas.
5. **Registra el resultado en la consola** y continúa levantando el servidor aunque haya avisos,
   para que el frontend siga accesible y el error quede visible.

**Salida típica por consola:**

```
Inicializando base de datos 'parkutp'
  Scripts SQL: C:\...\ParkUTP\backend\sql
  Base de datos: ya existia (parkutp)
  [OK  ] schema.sql: 4 sentencias aplicadas
  [OK  ] seed.sql: 2 sentencias aplicadas
  Base de datos lista (6 sentencias aplicadas)
ParkUTP corriendo en http://localhost:8080
```

**Advertencia a tener en cuenta:** `seed.sql` usa `ON CONFLICT … DO UPDATE` sobre la contraseña de
los usuarios de prueba, así que **en cada arranque se restablecen a `admin/admin123` y `juan/1234`**.
Si se cambia una contraseña en la base de datos, el siguiente arranque la revierte. Para evitarlo,
basta con editar `backend/sql/seed.sql` y usar `ON CONFLICT (username) DO NOTHING`.

---

## 3. Estructura de archivos

### 3.1 Backend — archivos creados

| Archivo | Descripción |
|---|---|
| `backend/src/main/java/com/parkutp/model/Ticket.java` | Modelo de la boleta: `id`, `reservationId`, datos del parking y del cliente, `entrada`/`salida` (epoch ms), `minutosEstancia`, `minutosFacturables`, `tarifaHora`, `subtotal`, `estado`. |
| `backend/src/main/java/com/parkutp/service/TicketException.java` | Excepción de dominio con el enum `Motivo` (`NO_ENCONTRADA`, `YA_EMITIDA`, `NO_ACTIVA`, `SIN_PERMISO`, `PARKING_NO_ENCONTRADO`). Permite mapear el error a un código HTTP sin acoplar la lógica de negocio al servidor. |
| `backend/src/main/java/com/parkutp/util/DatabaseInitializer.java` | Inicializador automático: localiza los scripts SQL, crea la base si falta, divide los scripts en sentencias, las vuelve idempotentes y las aplica. Incluye el parser de sentencias (comentarios `--` y `/* */`, comillas simples y dobles, bloques `$$…$$`). |

### 3.2 Backend — archivos modificados

| Archivo | Cambio |
|---|---|
| `backend/src/main/java/com/parkutp/server/HttpServerApp.java` | Se añadió la llamada `DatabaseInitializer.inicializar()` al principio de `main()`, antes de crear y levantar el servidor HTTP; y las rutas `GET /api/tickets/estimado`, `POST /api/tickets`, `GET /api/tickets` con su mapeo de errores por `Motivo`. |
| `backend/src/main/java/com/parkutp/service/ParkingManager.java` | Nuevos métodos `estimarTicket(reservationId)`, `crearTicket(reservationId, username)` (transaccional) y `listarTickets(username)`, más los privados `calcularTicket`, `existeTicket`, `generarIdTicket` y `mapearTicket`. |
| `backend/src/main/java/com/parkutp/util/DatabaseConnection.java` | La URL, el usuario y la contraseña se pueden sobrescribir con `-Dparkutp.db.url` / `PARKUTP_DB_URL` (y equivalentes para usuario y contraseña). Se añadieron `getUrl()`, `getUser()`, `getPassword()`, `getNombreBase()`, `getConnection(String database)` y `cambiarBase(...)`. Los valores por defecto no cambian. |
| `backend/sql/schema.sql` | Nueva tabla `tickets` (con `UNIQUE(reservation_id)` y sus claves foráneas), declarada **después** de `reservations` para que la FK sea válida. |

### 3.3 Frontend — archivos creados

| Archivo | Descripción |
|---|---|
| `frontend/js/boleta.js` | Módulo de boletas: `generarTextoBoleta(t)`, `construirHtmlBoleta(t)`, `construirDocumentoImpresion(t)`, `descargarBoletaTexto(t)`, `imprimirBoleta(t)`, `imprimirEnVentana(doc)`, `abrirModalBoleta(t)`, `cerrarModalBoleta()`, `descargarBoletaActual()`, `imprimirBoletaActual()`, además de `formatearFecha`, `formatearDuracion` y `formatearMoneda`. |

### 3.4 Frontend — archivos modificados

| Archivo | Cambio |
|---|---|
| `frontend/js/api.js` | Nuevos métodos `estimarTicket(reservationId)`, `crearTicket(reservationId, username)` y `getTickets(username)`. |
| `frontend/js/ui.js` | `escaparHtml(texto)` pasa a ser una utilidad compartida (estaba en `boleta.js`), para que lo usen todos los módulos. |
| `frontend/js/usuario.js` | `prepararBoleta()`, `confirmarEmisionBoleta()`, `cargarTickets()`; botón «Emitir boleta» por reserva `ACTIVA`; tabla «Mis Boletas» con acciones Ver / `.txt` / Imprimir; delegación de eventos con `data-accion` y `data-index`. |
| `frontend/usuario.html` | Sección «Mis Boletas», modal de confirmación del estimado y modal de la boleta emitida (con botones Descargar .txt e Imprimir). |
| `frontend/css/styles.css` | Estilos de la vista previa de la boleta, de la tabla de boletas, de los botones de acción y reglas de impresión. |

### 3.5 Documentación

| Archivo | Cambio |
|---|---|
| `INFORME_SISTEMA_BOLETAS.md` | Este documento (sustituye al informe previo en `.txt`). |
| `README.md` | Endpoints de tickets, sección de boletas, inicialización automática de la base de datos y configuración por variables de entorno. |
| `docs/boleta-ejemplo.txt` | **Eliminado**, sustituido por este `.md`. |

---

## 4. Reglas de cálculo

### 4.1 Fórmulas

```
minutosEstancia    = (salida − reservations.timestamp) / 60000
minutosContratados = reservations.horas × 60 + reservations.minutos
minutosFacturables = max(minutosEstancia, minutosContratados)

horasFacturables   = minutosFacturables / 60
tarifa             = tarifa_hora | tarifa_moto | tarifa_camioneta   (según el vehículo)
subtotal           = round(tarifa × horasFacturables, 2)
```

### 4.2 Qué significa cada regla

- **Tiempo de estancia real.** Se mide desde el instante en que se creó la reserva (`timestamp`) hasta
  la salida. Es el tiempo que el vehículo estuvo realmente en el parking.
- **Tiempo facturable = máximo entre el real y el contratado.** Si el vehículo se queda **más** tiempo
  del previsto, se cobra el tiempo real. Si se va **antes**, se cobra igualmente el tiempo contratado.
  Nunca se cobra menos de lo contratado.
- **Tarifa por tipo de vehículo.** `CARRO` → `tarifa_hora`, `MOTO` → `tarifa_moto`,
  `CAMIONETA` → `tarifa_camioneta`.
- **Redondeo a dos decimales** en el subtotal (`Math.round(x × 100) / 100`).

### 4.3 Liberación transaccional del espacio

`POST /api/tickets` se ejecuta dentro de una única transacción (`setAutoCommit(false)`):

| Paso | Sentencia | Efecto |
|---|---|---|
| 1 | `SELECT … FROM reservations WHERE id = ? FOR UPDATE` | Bloquea la reserva: dos emisiones simultáneas no pueden solaparse. |
| 2 | Validaciones | `403` si la reserva es de otro usuario · `409` si ya tiene boleta o no está `ACTIVA` · `404` si no existe. |
| 3 | `INSERT INTO tickets …` | Inserta la boleta (`UNIQUE(reservation_id)` impide el duplicado). |
| 4 | `UPDATE reservations SET estado = 'FINALIZADA'` | Cierra la reserva. |
| 5 | `UPDATE parkings SET espacios_ocupados = GREATEST(espacios_ocupados − 1, 0)` | Libera el espacio; `GREATEST` evita que el contador quede negativo. |
| 6 | `commit()` | Confirma los tres cambios a la vez. |

Si algo falla, se hace `rollback()`: o se guarda la boleta y se cierra la reserva y se libera el
espacio, o no se cambia nada. En los casos de error la base de datos queda **intacta**.

---

## 5. Endpoints de la API

### 5.1 `GET /api/tickets/estimado?reserva=<reservationId>`

Calcula el tiempo de estancia y la tarifa **sin modificar la base de datos**.

```jsonc
// 200 OK
{
  "status": "OK",
  "estimado": {
    "id": "", "reservationId": "R1792459323905",
    "parkingId": "P001", "parkingNombre": "Parking Central",
    "parkingDireccion": "Av.-principal 123",
    "username": "juan", "placa": "ABC-123", "tipoVehiculo": "CARRO",
    "espacioAsignado": "A01",
    "entrada": 1792448523905, "salida": 1792459324460,
    "minutosEstancia": 180, "minutosFacturables": 180,
    "tarifaHora": 5.0, "subtotal": 15.0, "estado": ""
  }
}
```

| Código | Situación |
|---|---|
| `200` | Estimado calculado |
| `400` | Falta el parámetro `reserva` |
| `404` | La reserva no existe |

### 5.2 `POST /api/tickets`

```jsonc
// cuerpo
{ "reservationId": "R1792459323905", "username": "juan" }

// 200 OK
{
  "status": "OK",
  "ticket": {
    "id": "T1792459324543274", "reservationId": "R1792459323905",
    "parkingId": "P001", "parkingNombre": "Parking Central",
    "parkingDireccion": "Av.-principal 123",
    "username": "juan", "placa": "ABC-123", "tipoVehiculo": "CARRO",
    "espacioAsignado": "A01",
    "entrada": 1792448523905, "salida": 1792459324460,
    "minutosEstancia": 180, "minutosFacturables": 180,
    "tarifaHora": 5.0, "subtotal": 15.0, "estado": "PAGADA"
  }
}
```

| Código | Situación |
|---|---|
| `200` | Boleta emitida |
| `400` | Falta `reservationId`, JSON inválido o el parking de la reserva no existe |
| `403` | La reserva pertenece a otro usuario |
| `404` | La reserva no existe |
| `409` | La reserva ya tiene boleta o ya no está `ACTIVA` |

### 5.3 `GET /api/tickets[?username=<usuario>]`

Devuelve un **array JSON** (igual que `/api/parkings` y `/api/reservations`). Con `username` filtra
las boletas de un cliente; sin él, devuelve todas. Cada elemento incluye el nombre y la dirección
del parking gracias al `LEFT JOIN` con `parkings`.

### 5.4 Tabla resumen de endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/login` | Autenticación (`username`, `password`) |
| `GET` | `/api/parkings` | Lista de parkings |
| `POST` | `/api/parkings` | Crea un parking (ADMIN) |
| `PUT` | `/api/parkings?id=` | Actualiza un parking (ADMIN) |
| `DELETE` | `/api/parkings?id=` | Elimina un parking (ADMIN) |
| `GET` | `/api/reservations` | Lista de reservas |
| `GET` | `/api/reservations?username=` | Reservas de un usuario |
| `POST` | `/api/reservations` | Crea una reserva |
| **`GET`** | **`/api/tickets/estimado?reserva=`** | **Calcula estancia y tarifa sin emitir la boleta** |
| **`POST`** | **`/api/tickets`** | **Emite la boleta** (`reservationId`, `username`) |
| **`GET`** | **`/api/tickets`** | **Lista de boletas** |
| **`GET`** | **`/api/tickets?username=`** | **Boletas de un usuario** |

---

## 6. La boleta de 40 columnas

### 6.1 Formato de texto plano (descargable)

Es la salida real de `generarTextoBoleta()` (`frontend/js/boleta.js`) para una reserva de 2 h
contratadas en *Parking Central* que estuvo 3 h dentro (simulando la entrada 3 h antes en la base
de datos):

```text
========================================
        PARKUTP - BOLETA DE PAGO
========================================
Boleta Nro:            T1792459324543274
Emitida:             19/10/2026 20:22:04
----------------------------------------
Parking:                 Parking Central
Direccion:             Av.-principal 123
Espacio:                             A01
----------------------------------------
Usuario:                            juan
Placa:                           ABC-123
Vehiculo:                          CARRO
----------------------------------------
Entrada:             19/10/2026 17:22:03
Salida:              19/10/2026 20:22:04
Estancia:                            3 h
Facturable:                          3 h
Tarifa por hora:                S/. 5.00
----------------------------------------
TOTAL:                         S/. 15.00
========================================
Gracias por su visita.
Conserve este comprobante.
========================================
```

**Anatomía de la boleta** (etiqueta de 16 columnas, valor alineado a la derecha, 40 columnas en total):

| Bloque | Contenido |
|---|---|
| Cabecera | `PARKUTP - BOLETA DE PAGO` entre dos líneas de `=` |
| Identificación | Número de boleta, fecha y hora de emisión |
| Ubicación | Parking, dirección y espacio asignado |
| Cliente | Usuario, placa y tipo de vehículo |
| Tiempo | Entrada, salida, estancia real y tiempo facturable |
| Cobro | Tarifa por hora y `TOTAL` |
| Pie | Mensaje de agradecimiento y de conservación del comprobante |

**Descarga.** `descargarBoletaTexto(t)` construye un `Blob` de tipo `text/plain;charset=utf-8`
**con BOM** (`﻿`) para que el Bloc de notas de Windows muestre bien los acentos, y lo descarga como
`boleta-<id>.txt`.

### 6.2 Versión imprimible (HTML)

`construirDocumentoImpresion(t)` genera un documento HTML completo y autocontenido —con su propia
hoja de estilos y sus reglas `@media print`— que se inyecta en un `iframe` oculto para llamar a
`print()`. Así la boleta impresa es exactamente la vista previa, sin la interfaz de la web alrededor:

```
┌──────────────────────────────────────┐
│              PARKUTP                 │
│           BOLETA DE PAGO             │
├──────────────────────────────────────┤
│ Boleta Nro          T1792459324543274 │
│ Emitida             19/10/2026 20:22  │
│ Parking             Parking Central   │
│ Direccion           Av.-principal 123  │
│ Espacio             A01               │
│ Usuario             juan              │
│ Placa               ABC-123           │
│ Vehiculo            CARRO             │
│ Entrada             19/10/2026 17:22  │
│ Salida              19/10/2026 20:22  │
│ Tiempo de estancia  3 h               │
│ Tiempo facturable   3 h               │
│ Tarifa por hora     S/. 5.00          │
├──────────────────────────────────────┤
│ TOTAL                    S/. 15.00    │
├──────────────────────────────────────┤
│  Gracias por su visita. Conserve      │
│  este comprobante.                    │
└──────────────────────────────────────┘
```

Si el navegador bloquea la impresión, `imprimirBoleta()` recurre a `imprimirEnVentana()`, que abre
el mismo documento en una pestaña nueva.

---

## 7. Resultados de las pruebas

Todas las comprobaciones se ejecutaron sobre el **build final** (el que está en ejecución).

### 7.1 Compilación

| Comprobación | Resultado |
|---|---|
| `javac --release 17 -Xlint:all` (backend completo: 9 fuentes → 12 clases) | **exit 0, sin warnings** |
| `node --check` sobre los 6 archivos JS del frontend | **todos correctos** |

### 7.2 Pruebas unitarias

| Suite | Cobertura | Resultado |
|---|---|---|
| Módulo de boletas (Node) | Formato de duración y moneda · ancho máximo de 40 columnas · líneas separadoras · HTML de la boleta · documento de impresión · **escape de HTML (XSS)** · descarga del `.txt` · impresión por `iframe` | **31/31 OK** |
| `DatabaseInitializer` (Java) | División de sentencias (comentarios `--` y `/* */`, `;` dentro de cadenas, identificadores con comillas dobles, bloques `$$…$$`, script vacío) · transformación idempotente de `CREATE TABLE/INDEX/SCHEMA/SEQUENCE` · sin duplicar `IF NOT EXISTS` · los `INSERT` no se tocan · los scripts reales del proyecto (4 sentencias de esquema, 2 de seed, orden y FKs correctos) | **21/21 OK** |

> La suite de `DatabaseInitializer` detectó un fallo real en el parser de bloques `$$…$$`, que se
> corrigió antes de la entrega.

### 7.3 Pruebas de API (20 aserciones)

| Caso | Resultado |
|---|---|
| `POST /api/login` válido | OK |
| `POST /api/reservations` crea la reserva | OK |
| `GET /api/tickets/estimado` → estado, `reservationId`, placa, y **no muta la BD** | OK |
| `POST /api/tickets` → estado, `reservationId`, nombre y dirección del parking, `subtotal` igual al estimado | OK |
| `GET /api/tickets` → array JSON, incluye el ticket, filtra por usuario | OK |
| `409` boleta duplicada | OK |
| `404` reserva inexistente | OK |
| `400` falta `reservationId` | OK |
| `400` JSON inválido | OK |
| `403` reserva de otro usuario | OK |
| El propietario sí puede emitir su boleta | OK |
| En BD: reserva en `FINALIZADA`, 1 ticket, `espacios_ocupados = 0` | OK |
| **RESULTADO** | **PASS = 20, FAIL = 0** |

### 7.4 Prueba end-to-end en navegador real

Chrome headless con el protocolo DevTools, recorriendo la interfaz como un usuario:

| Comprobación | Resultado |
|---|---|
| Sesión de usuario establecida en `localStorage` | OK |
| Reserva de prueba creada por la API | OK |
| La vista de usuario carga (`title = ParkUTP - Usuario`) | OK |
| La tabla de reservas muestra filas y el botón «Emitir boleta» | OK |
| La tabla de boletas muestra filas | OK |
| Se abre el modal de confirmación con el estimado (estancia, facturable, tarifa, total) | OK |
| Se emite la boleta y se abre el modal con la vista previa | OK |
| La vista previa muestra el encabezado y el total | OK |
| Existen los botones «Descargar .txt» e «Imprimir» | OK |
| La descarga genera `boleta-<id>.txt` | OK |
| La reserva queda `FINALIZADA` y el espacio queda libre | OK |
| **Errores de JavaScript en la página** | **0** |
| **RESULTADO** | **20/20 OK** |

### 7.5 Inicialización automática de la base de datos

| Escenario | Resultado |
|---|---|
| **Base de datos inexistente** | Se crea (`CREATE DATABASE`), se aplican `schema.sql` (4 sentencias) y `seed.sql` (2), y el servidor arranca. Tablas, usuarios y parkings verificados por SQL. |
| **Base ya inicializada (segundo arranque)** | «Base de datos: ya existia», 6 sentencias aplicadas, **cero errores** y **cero duplicados** (2 usuarios, 3 parkings). El login sigue funcionando. |
| **Esquema desactualizado (falta la tabla `tickets`)** | La tabla se recrea sola en el arranque, sin tocar las demás y sin errores. |
| **Base de datos real del proyecto** | Se inicializa sin errores y **los datos existentes se conservan** (antes 1 reserva / 1 ticket, después 1 reserva / 1 ticket). |

---

## 8. Puesta en marcha

```bash
# 1. Base de datos (opcional: el backend la crea y la inicializa solo)
createdb -U postgres parkutp

# 2. Backend — desde la raíz del proyecto
cd backend
mvn clean package
java -cp "target/classes;target/parkutp-backend-1.0.0.jar" com.parkutp.server.HttpServerApp
# o, con las clases compiladas a mano:
# javac --release 17 -d classes $(find src/main/java -name "*.java")
# java -cp "classes;RUTA/jackson-databind.jar;RUTA/jackson-core.jar;RUTA/jackson-annotations.jar;RUTA/postgresql.jar" \
#      com.parkutp.server.HttpServerApp
```

El servidor **debe ejecutarse desde la raíz del proyecto**, porque sirve los archivos estáticos de
`frontend/` y busca los scripts SQL en `backend/sql/`.

| Variable / propiedad | Valor por defecto | Para qué sirve |
|---|---|---|
| `-Dport=8081` o `PORT` | `8080` | Puerto del servidor |
| `-Dparkutp.db.url` o `PARKUTP_DB_URL` | `jdbc:postgresql://localhost:5432/parkutp` | Base de datos |
| `-Dparkutp.db.user` o `PARKUTP_DB_USER` | `postgres` | Usuario de PostgreSQL |
| `-Dparkutp.db.password` o `PARKUTP_DB_PASSWORD` | *(en `DatabaseConnection.java`)* | Contraseña de PostgreSQL |
| `-Dparkutp.sql.dir` o `PARKUTP_SQL_DIR` | `backend/sql` | Carpeta con `schema.sql` y `seed.sql` |

Usuarios de prueba: `admin / admin123` (ADMIN) y `juan / 1234` (USER).

---

## 9. Limitaciones conocidas

- El panel de administración **no** emite boletas ni lista los tickets: la emisión es
  exclusivamente desde la vista del cliente.
- La impresión web se verifica comprobando que se invoca `print()` y que el documento impreso
  contiene la boleta; la salida física de una impresora no puede comprobarse en un entorno
  headless.
- El backend **no autentica las peticiones por token**: la seguridad depende de que el frontend no
  exponga el panel de administración.
- Las contraseñas se guardan en texto plano (comparación directa en `ParkingManager.login`). Para
  producción conviene migrar a un hash (por ejemplo BCrypt) y ajustar `ParkingManager.login` y
  `seed.sql`.
- `CREATE TABLE IF NOT EXISTS` **no modifica** una tabla que ya existe con otra estructura: si
  cambias las columnas de una tabla, hay que migrarla a mano o recrear la base de datos.
- El `seed.sql` restablece las credenciales de prueba en cada arranque (ver el aviso de la
  sección 2.2).

---

*Documento generado a partir del código y de las ejecuciones de prueba del proyecto ParkUTP.*
