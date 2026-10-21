# ParkUTP

Sistema de gestion de estacionamientos (backend Java + frontend estatico HTML/JS).

- **Backend:** Java 17+, `com.sun.net.httpserver` + JDBC PostgreSQL + Jackson
- **Frontend:** HTML/CSS/JS estatico servido por el propio backend
- **Base de datos:** PostgreSQL

## Estructura

```
backend/
  src/main/java/com/parkutp/
    server/HttpServerApp.java     # servidor HTTP + endpoints /api
    service/ParkingManager.java    # logica de negocio (login, parkings, reservas, tickets)
    service/TicketException.java   # errores de dominio al emitir una boleta
    model/                        # Parking, Reservation, User, Ticket
    util/DatabaseConnection.java   # credenciales de conexion
    util/DatabaseInitializer.java  # inicializacion automatica del esquema y los datos
  sql/
    schema.sql                    # DDL de users, parkings, reservations, tickets
    seed.sql                      # usuarios y parkings de prueba
frontend/
  index.html, admin.html, usuario.html, css/, js/
  js/boleta.js                   # generacion, descarga e impresion de la boleta
docs/
  arquitectura.md, casos_de_uso.md
INFORME_SISTEMA_BOLETAS.md       # informe completo del sistema de tickets y boletas
```

## 1. Base de datos

La base de datos **se inicializa sola al arrancar el backend**: se crea si no existe y se aplican
`backend/sql/schema.sql` y `backend/sql/seed.sql` con sentencias idempotentes
(`CREATE TABLE IF NOT EXISTS`, `ON CONFLICT`). Si prefieres hacerlo a mano:

```bash
createdb -U postgres parkutp
psql -U postgres -d parkutp -f backend/sql/schema.sql
psql -U postgres -d parkutp -f backend/sql/seed.sql
```

La conexion se configura en `backend/src/main/java/com/parkutp/util/DatabaseConnection.java`
(URL, usuario y contrasena de PostgreSQL). Por defecto:

```
jdbc:postgresql://localhost:5432/parkutp
usuario: postgres
```

Tambien se puede sobrescribir sin tocar el codigo:

| Propiedad del sistema | Variable de entorno | Valor por defecto |
|---|---|---|
| `-Dparkutp.db.url` | `PARKUTP_DB_URL` | `jdbc:postgresql://localhost:5432/parkutp` |
| `-Dparkutp.db.user` | `PARKUTP_DB_USER` | `postgres` |
| `-Dparkutp.db.password` | `PARKUTP_DB_PASSWORD` | el de `DatabaseConnection.java` |
| `-Dparkutp.sql.dir` | `PARKUTP_SQL_DIR` | `backend/sql` |

> Si cambias la URL, el usuario o la contrasena, reinicia el backend.

## 2. Backend

```bash
cd backend
mvn clean package
java -cp "target/classes;target/parkutp-backend-1.0.0.jar" com.parkutp.server.HttpServerApp
```

El puerto por defecto es `8080` y se puede cambiar con `-Dport=8081` o la variable de entorno `PORT`.

El servidor sirve los archivos estaticos de `frontend/`. Puede ejecutarse desde la raiz
del proyecto o desde `backend/`; en ambos casos encuentra la carpeta del frontend.

## 3. Frontend

Abre `http://localhost:8080` en el navegador.

La pantalla de administrador incluye notificaciones internas de nuevas reservas y boletas,
ademas de reportes graficos de ingresos por boletas, reservas y ocupacion. Los reportes
permiten elegir entre los ultimos 7, 30 o 90 dias; las notificaciones se guardan en el
navegador y se consultan cada 20 segundos. Los ingresos reflejan boletas emitidas, no pagos
procesados por una pasarela.

Las pantallas de administrador y usuario muestran un mapa de espacios libres y ocupados,
actualizado cada 30 segundos. El mapa representa filas de espacios identificados por letra
y numero; no es un plano arquitectonico del estacionamiento.

Usuarios de prueba:

| Usuario  | Contrasena | Rol    |
|----------|------------|--------|
| `admin`  | `admin123` | ADMIN  |
| `juan`   | `1234`     | USER   |

## Endpoints

| Metodo | Ruta                     | Descripcion                                  |
|--------|--------------------------|----------------------------------------------|
| POST   | `/api/login`             | Autenticacion (`username`, `password`)        |
| GET    | `/api/parkings`          | Lista de parkings                             |
| POST   | `/api/parkings`          | Crea un parking (ADMIN)                       |
| PUT    | `/api/parkings?id=`      | Actualiza un parking (ADMIN)                  |
| DELETE | `/api/parkings?id=`      | Elimina un parking (ADMIN)                    |
| GET    | `/api/reservations`      | Lista de reservas                             |
| GET    | `/api/reservations?username=` | Reservas de un usuario                   |
| POST   | `/api/reservations`      | Crea una reserva                              |
| GET    | `/api/tickets/estimado?reserva=` | Calcula estancia y tarifa de una reserva |
| POST   | `/api/tickets`           | Emite la boleta (`reservationId`, `username`)  |
| GET    | `/api/tickets`           | Lista de boletas                               |
| GET    | `/api/tickets?username=` | Boletas de un usuario                          |

## Boletas de estancia (tickets)

Al emitir una boleta el backend:

1. Calcula el **tiempo de estancia** real (desde `reservations.timestamp` hasta la salida).
2. Calcula el **tiempo facturable** = max(estancia real, tiempo contratado en la reserva).
3. Aplica la tarifa por hora del vehiculo al tiempo facturable y redondea a 2 decimales.
4. Registra el ticket, pasa la reserva a `FINALIZADA` y libera el espacio ocupado.

```bash
# 1. Consultar el estimado (no modifica nada)
curl "http://localhost:8080/api/tickets/estimado?reserva=R1792458646049"

# 2. Emitir la boleta
curl -X POST http://localhost:8080/api/tickets \
     -H "Content-Type: application/json" \
     -d '{"reservationId":"R1792458646049","username":"juan"}'
```

Codigos de respuesta: `403` la reserva es de otro usuario, `404` la reserva no existe,
`409` la reserva ya tiene boleta o no esta activa, `400` falta `reservationId`.

En el frontend, `frontend/js/boleta.js` expone:

| Funcion                  | Que hace                                                    |
|--------------------------|-------------------------------------------------------------|
| `generarTextoBoleta(t)`  | Boleta en texto plano de 40 columnas                        |
| `construirHtmlBoleta(t)` | Boleta en HTML para la vista previa                         |
| `descargarBoletaTexto(t)`| Descarga `boleta-<id>.txt` (con BOM para Bloc de notas)      |
| `imprimirBoleta(t)`      | Impresion web via iframe oculto (sin bloquear popups)       |
| `abrirModalBoleta(t)`    | Muestra la boleta en un modal con descarga e impresion      |

El informe completo del sistema de tickets y boletas (calculo, endpoints, estructura de archivos,
ejemplo de la boleta y resultados de las pruebas) esta en
[`INFORME_SISTEMA_BOLETAS.md`](INFORME_SISTEMA_BOLETAS.md).

## Problemas frecuentes

**"Credenciales invalidas" al iniciar sesion**

- La causa mas comun es que la tabla `users` este vacia: el backend compara
  `username` y `password` tal cual contra la base de datos.
  Revisa con `psql -U postgres -d parkutp -c "SELECT * FROM users;"` y, si no hay
  filas, ejecuta `backend/sql/seed.sql`.
- Verifica que la contrasena de `DatabaseConnection.java` sea la correcta y que
  PostgreSQL este escuchando en el puerto configurado.
- El backend distingue los casos: `401` son credenciales incorrectas, `500` es un
  error de conexion a la base de datos (el detalle real se escribe en la consola
  del servidor).
- Las contrasenas se guardan en texto plano porque la comparacion es directa.
  Para produccion se recomienda migrar a un hash (por ejemplo BCrypt) y ajustar
  `ParkingManager.login` y el `seed.sql`.
