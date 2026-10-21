package com.parkutp.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkutp.model.Parking;
import com.parkutp.model.Reservation;
import com.parkutp.model.Ticket;
import com.parkutp.model.User;
import com.parkutp.service.ParkingManager;
import com.parkutp.service.TicketException;
import com.parkutp.util.DatabaseInitializer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.Executors;

public class HttpServerApp {

    // Puerto configurable: -Dport=8081 o variable de entorno PORT (por defecto 8080)
    private static final int PORT = Integer.parseInt(
            System.getProperty("port",
                    System.getenv().getOrDefault("PORT", "8080")));
    private static final Path FRONTEND_DIR = resolverDirectorioFrontend();
    private static final ParkingManager manager = new ParkingManager();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        // 1. Inicializacion automatica de la base de datos: crea la base si falta
        //    y aplica schema.sql / seed.sql con sentencias idempotentes.
        DatabaseInitializer.Resultado inicializacion = DatabaseInitializer.inicializar();
        if (!inicializacion.isExitoso()) {
            System.err.println("Aviso: la base de datos no quedo completamente inicializada.");
        }

        // 2. Servidor HTTP
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/api/login", HttpServerApp::handleLogin);
        server.createContext("/api/parkings", HttpServerApp::handleParkings);
        server.createContext("/api/reservations", HttpServerApp::handleReservations);
        server.createContext("/api/tickets/estimado", HttpServerApp::handleTicketEstimado);
        server.createContext("/api/tickets", HttpServerApp::handleTickets);
        server.createContext("/", HttpServerApp::handleStatic);

        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("ParkUTP corriendo en http://localhost:" + PORT);
        System.out.println("Usuarios de prueba:");
        System.out.println("- admin / admin123 (ADMIN)");
        System.out.println("- juan  / 1234     (USER)");
    }

    // ==================== LOGIN ====================
    private static void handleLogin(HttpExchange ex) throws IOException {
        agregarCORS(ex);

        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        if (!"POST".equals(ex.getRequestMethod())) {
            responder(ex, 405, "{\"status\":\"ERROR\",\"message\":\"Metodo no permitido\"}");
            return;
        }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        JsonNode json;
        try {
            json = mapper.readTree(body);
        } catch (Exception e) {
            responder(ex, 400, "{\"status\":\"ERROR\",\"message\":\"JSON invalido\"}");
            return;
        }

        JsonNode nodoUser = json.get("username");
        JsonNode nodoPass = json.get("password");
        if (nodoUser == null || nodoPass == null) {
            responder(ex, 400, "{\"status\":\"ERROR\",\"message\":\"Faltan usuario o contrasena\"}");
            return;
        }

        String username = nodoUser.asText();
        String password = nodoPass.asText();

        if (username.isBlank() || password.isBlank()) {
            responder(ex, 400, "{\"status\":\"ERROR\",\"message\":\"Usuario y contrasena son obligatorios\"}");
            return;
        }

        User user;
        try {
            user = manager.login(username, password);
        } catch (SQLException e) {
            System.err.println("Error de base de datos en login: " + e.getMessage());
            responder(ex, 500, "{\"status\":\"ERROR\",\"message\":\"Error de conexion a la base de datos\"}");
            return;
        }

        if (user == null) {
            responder(ex, 401, "{\"status\":\"ERROR\",\"message\":\"Credenciales invalidas\"}");
        } else {
            String resp = String.format(
                "{\"status\":\"OK\",\"username\":\"%s\",\"role\":\"%s\"}",
                user.getUsername(), user.getRole()
            );
            responder(ex, 200, resp);
        }
    }

    // ==================== PARKINGS ====================
    private static void handleParkings(HttpExchange ex) throws IOException {
        agregarCORS(ex);

        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        String method = ex.getRequestMethod();

        // GET
        if ("GET".equals(method)) {
            String json = mapper.writeValueAsString(manager.listarParkings());
            responder(ex, 200, json);
            return;
        }

        // POST
        if ("POST".equals(method)) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode json = mapper.readTree(body);

            String nombre = json.get("nombre").asText();
            String direccion = json.get("direccion").asText();
            int espacios = json.get("espaciosTotales").asInt();
            double tarifaHora = json.get("tarifaHora").asDouble();
            double tarifaMoto = json.get("tarifaMoto").asDouble();
            double tarifaCamioneta = json.get("tarifaCamioneta").asDouble();

            Parking p = manager.crearParking(nombre, direccion, espacios,
                                              tarifaHora, tarifaMoto, tarifaCamioneta);
            if (p == null) {
                responder(ex, 400, "{\"status\":\"ERROR\",\"message\":\"No se pudo crear el parking\"}");
            } else {
                responder(ex, 200, "{\"status\":\"OK\",\"parking\":" +
                         mapper.writeValueAsString(p) + "}");
            }
            return;
        }

        // PUT
        if ("PUT".equals(method)) {
            String query = ex.getRequestURI().getQuery();
            if (query == null || !query.startsWith("id=")) {
                responder(ex, 400, "{\"status\":\"ERROR\",\"message\":\"Falta id\"}");
                return;
            }
            String id = query.substring(3);

            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode json = mapper.readTree(body);

            String nombre = json.get("nombre").asText();
            String direccion = json.get("direccion").asText();
            int espacios = json.get("espaciosTotales").asInt();
            double tarifaHora = json.get("tarifaHora").asDouble();
            double tarifaMoto = json.get("tarifaMoto").asDouble();
            double tarifaCamioneta = json.get("tarifaCamioneta").asDouble();

            boolean ok = manager.actualizarParking(id, nombre, direccion, espacios,
                                                    tarifaHora, tarifaMoto, tarifaCamioneta);
            if (ok) {
                responder(ex, 200, "{\"status\":\"OK\"}");
            } else {
                responder(ex, 400, "{\"status\":\"ERROR\",\"message\":\"No se pudo actualizar\"}");
            }
            return;
        }

        // DELETE
        if ("DELETE".equals(method)) {
            String query = ex.getRequestURI().getQuery();
            if (query == null || !query.startsWith("id=")) {
                responder(ex, 400, "{\"status\":\"ERROR\",\"message\":\"Falta id\"}");
                return;
            }
            String id = query.substring(3);

            boolean ok = manager.eliminarParking(id);
            if (ok) {
                responder(ex, 200, "{\"status\":\"OK\"}");
            } else {
                responder(ex, 400, "{\"status\":\"ERROR\",\"message\":\"No se pudo eliminar (tiene reservas activas)\"}");
            }
            return;
        }

        responder(ex, 405, "{\"status\":\"ERROR\",\"message\":\"Metodo no permitido\"}");
    }

    // ==================== RESERVAS ====================
    private static void handleReservations(HttpExchange ex) throws IOException {
        agregarCORS(ex);

        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        String method = ex.getRequestMethod();

        if ("GET".equals(method)) {
            String query = ex.getRequestURI().getQuery();
            Object resultado;

            if (query != null && query.startsWith("username=")) {
                String user = query.substring("username=".length());
                resultado = manager.reservasDeUsuario(user);
            } else {
                resultado = manager.todasLasReservas();
            }

            responder(ex, 200, mapper.writeValueAsString(resultado));
            return;
        }

        if ("POST".equals(method)) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode json = mapper.readTree(body);

            String parkingId = json.get("parkingId").asText();
            String username = json.get("username").asText();
            String placa = json.get("placa").asText();
            String tipoVehiculo = json.has("tipoVehiculo") ? json.get("tipoVehiculo").asText() : "CARRO";
            int horas = json.has("horas") ? json.get("horas").asInt() : 0;
            int minutos = json.has("minutos") ? json.get("minutos").asInt() : 0;

            Reservation r = manager.crearReserva(parkingId, username, placa,
                                                  tipoVehiculo, horas, minutos);
            if (r == null) {
                responder(ex, 400, "{\"status\":\"ERROR\",\"message\":\"Sin espacios disponibles\"}");
            } else {
                String resp = "{\"status\":\"OK\",\"reservation\":" +
                              mapper.writeValueAsString(r) + "}";
                responder(ex, 200, resp);
            }
            return;
        }

        responder(ex, 405, "{\"status\":\"ERROR\",\"message\":\"Metodo no permitido\"}");
    }

    // ==================== TICKETS / BOLETAS ====================

    /** GET /api/tickets/estimado?reserva=R123 -> calcula estancia y tarifa sin emitir la boleta. */
    private static void handleTicketEstimado(HttpExchange ex) throws IOException {
        agregarCORS(ex);

        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        if (!"GET".equals(ex.getRequestMethod())) {
            responder(ex, 405, "{\"status\":\"ERROR\",\"message\":\"Metodo no permitido\"}");
            return;
        }

        String query = ex.getRequestURI().getQuery();
        if (query == null || !query.startsWith("reserva=")) {
            responder(ex, 400, "{\"status\":\"ERROR\",\"message\":\"Falta el parametro reserva\"}");
            return;
        }
        String reservaId = query.substring("reserva=".length());

        Ticket estimado = manager.estimarTicket(reservaId);
        if (estimado == null) {
            responder(ex, 404, "{\"status\":\"ERROR\",\"message\":\"La reserva no existe\"}");
            return;
        }

        responder(ex, 200, "{\"status\":\"OK\",\"estimado\":" +
                 mapper.writeValueAsString(estimado) + "}");
    }

    /** GET /api/tickets[?username=] lista boletas, POST /api/tickets emite una boleta. */
    private static void handleTickets(HttpExchange ex) throws IOException {
        agregarCORS(ex);

        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        String method = ex.getRequestMethod();

        if ("GET".equals(method)) {
            String query = ex.getRequestURI().getQuery();
            String username = null;
            if (query != null && query.startsWith("username=")) {
                username = query.substring("username=".length());
            }
            String json = mapper.writeValueAsString(manager.listarTickets(username));
            responder(ex, 200, json);
            return;
        }

        if ("POST".equals(method)) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            JsonNode json;
            try {
                json = mapper.readTree(body);
            } catch (Exception e) {
                responder(ex, 400, "{\"status\":\"ERROR\",\"message\":\"JSON invalido\"}");
                return;
            }

            JsonNode nodoReserva = json.get("reservationId");
            JsonNode nodoUser = json.get("username");
            if (nodoReserva == null) {
                responder(ex, 400, "{\"status\":\"ERROR\",\"message\":\"Falta reservationId\"}");
                return;
            }

            try {
                Ticket t = manager.crearTicket(nodoReserva.asText(),
                        nodoUser != null ? nodoUser.asText() : null);

                if (t == null) {
                    responder(ex, 500, "{\"status\":\"ERROR\",\"message\":\"No se pudo emitir la boleta\"}");
                    return;
                }

                responder(ex, 200, "{\"status\":\"OK\",\"ticket\":" +
                         mapper.writeValueAsString(t) + "}");
            } catch (TicketException e) {
                responder(ex, codigoPorMotivo(e.getMotivo()),
                        "{\"status\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}");
            }
            return;
        }

        responder(ex, 405, "{\"status\":\"ERROR\",\"message\":\"Metodo no permitido\"}");
    }

    private static int codigoPorMotivo(TicketException.Motivo motivo) {
        switch (motivo) {
            case NO_ENCONTRADA:         return 404;
            case SIN_PERMISO:           return 403;
            case YA_EMITIDA:
            case NO_ACTIVA:             return 409;
            case PARKING_NO_ENCONTRADO: return 400;
            default:                    return 400;
        }
    }

    // ==================== ARCHIVOS ESTATICOS ====================
    private static void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";

        Path file = FRONTEND_DIR.resolve(path.substring(1)).normalize();
        if (!file.startsWith(FRONTEND_DIR) || !Files.isRegularFile(file)) {
            String msg = "404 Not Found: " + path;
            byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(404, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
            return;
        }

        ex.getResponseHeaders().set("Content-Type", getContentType(path));
        ex.sendResponseHeaders(200, Files.size(file));

        try (OutputStream os = ex.getResponseBody();
             InputStream fis = Files.newInputStream(file)) {
            fis.transferTo(os);
        }
    }

    private static Path resolverDirectorioFrontend() {
        Path directorioActual = Path.of("").toAbsolutePath().normalize();
        Path frontend = buscarFrontendDesde(directorioActual);

        if (frontend == null) {
            try {
                Path ubicacionClases = Path.of(
                        HttpServerApp.class.getProtectionDomain()
                                .getCodeSource().getLocation().toURI());
                frontend = buscarFrontendDesde(ubicacionClases);
            } catch (URISyntaxException e) {
                System.err.println("No se pudo resolver la ubicacion de las clases del servidor: "
                        + e.getMessage());
            }
        }

        if (frontend != null) {
            System.out.println("Frontend: " + frontend);
            return frontend;
        }

        Path frontendEsperado = directorioActual.resolve("frontend").normalize();
        System.err.println("No se encontro frontend/index.html. Se busco desde el directorio actual "
                + "y desde la ubicacion de las clases del servidor.");
        return frontendEsperado;
    }

    private static Path buscarFrontendDesde(Path ubicacion) {
        Path directorio = Files.isDirectory(ubicacion) ? ubicacion : ubicacion.getParent();
        while (directorio != null) {
            Path candidato = directorio.resolve("frontend");
            if (Files.isDirectory(candidato) && Files.isRegularFile(candidato.resolve("index.html"))) {
                return candidato;
            }
            directorio = directorio.getParent();
        }
        return null;
    }

    private static String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=UTF-8";
        if (path.endsWith(".css"))  return "text/css; charset=UTF-8";
        if (path.endsWith(".js"))   return "application/javascript; charset=UTF-8";
        if (path.endsWith(".json")) return "application/json; charset=UTF-8";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".jpg"))  return "image/jpeg";
        return "text/plain; charset=UTF-8";
    }

    // ==================== UTILIDADES ====================
    private static void agregarCORS(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void responder(HttpExchange ex, int code, String body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}