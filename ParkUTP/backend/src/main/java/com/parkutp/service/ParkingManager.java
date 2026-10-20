package com.parkutp.service;

import com.parkutp.model.Parking;
import com.parkutp.model.Reservation;
import com.parkutp.model.Ticket;
import com.parkutp.model.User;
import com.parkutp.util.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ParkingManager {

    // ==================== USUARIOS ====================
    /**
     * @return el usuario si las credenciales coinciden, o null si no coinciden.
     * @throws SQLException si falla la conexion o la consulta (no es un error de credenciales).
     */
    public User login(String username, String password) throws SQLException {
        String sql = "SELECT username, password, role FROM users WHERE username = ? AND password = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getString("role")
                    );
                }
            }
        }
        return null;
    }

    // ==================== PARKINGS ====================
    public List<Parking> listarParkings() {
        List<Parking> lista = new ArrayList<>();
        String sql = "SELECT * FROM parkings ORDER BY id";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                lista.add(mapearParking(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error listando parkings: " + e.getMessage());
        }
        return lista;
    }

    public Parking buscarParking(String id) {
        String sql = "SELECT * FROM parkings WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapearParking(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error buscando parking: " + e.getMessage());
        }
        return null;
    }

    private Parking mapearParking(ResultSet rs) throws SQLException {
        Parking p = new Parking();
        p.setId(rs.getString("id"));
        p.setNombre(rs.getString("nombre"));
        p.setDireccion(rs.getString("direccion"));
        p.setEspaciosTotales(rs.getInt("espacios_totales"));
        p.setEspaciosOcupados(rs.getInt("espacios_ocupados"));
        p.setTarifaHora(rs.getDouble("tarifa_hora"));
        p.setTarifaMoto(rs.getDouble("tarifa_moto"));
        p.setTarifaCamioneta(rs.getDouble("tarifa_camioneta"));
        return p;
    }

    // ==================== CRUD PARKINGS ====================
    public Parking crearParking(String nombre, String direccion,
                                int espaciosTotales,
                                double tarifaHora, double tarifaMoto, double tarifaCamioneta) {
        String sql = "INSERT INTO parkings " +
            "(id, nombre, direccion, espacios_totales, espacios_ocupados, " +
            " tarifa_hora, tarifa_moto, tarifa_camioneta) " +
            "VALUES (?, ?, ?, ?, 0, ?, ?, ?)";

        String id = generarIdParking();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            ps.setString(2, nombre);
            ps.setString(3, direccion);
            ps.setInt(4, espaciosTotales);
            ps.setDouble(5, tarifaHora);
            ps.setDouble(6, tarifaMoto);
            ps.setDouble(7, tarifaCamioneta);

            int filas = ps.executeUpdate();
            if (filas > 0) {
                return buscarParking(id);
            }
        } catch (SQLException e) {
            System.err.println("Error creando parking: " + e.getMessage());
        }
        return null;
    }

    public boolean actualizarParking(String id, String nombre, String direccion,
                                      int espaciosTotales,
                                      double tarifaHora, double tarifaMoto,
                                      double tarifaCamioneta) {
        String sql = "UPDATE parkings SET " +
            "nombre = ?, direccion = ?, espacios_totales = ?, " +
            "tarifa_hora = ?, tarifa_moto = ?, tarifa_camioneta = ? " +
            "WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, nombre);
            ps.setString(2, direccion);
            ps.setInt(3, espaciosTotales);
            ps.setDouble(4, tarifaHora);
            ps.setDouble(5, tarifaMoto);
            ps.setDouble(6, tarifaCamioneta);
            ps.setString(7, id);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error actualizando parking: " + e.getMessage());
            return false;
        }
    }

    public boolean eliminarParking(String id) {
        try (Connection conn = DatabaseConnection.getConnection()) {

            String sqlCheck = "SELECT COUNT(*) FROM reservations " +
                              "WHERE parking_id = ? AND estado = 'ACTIVA'";
            try (PreparedStatement ps = conn.prepareStatement(sqlCheck)) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        System.err.println("No se puede eliminar: tiene reservas activas");
                        return false;
                    }
                }
            }

            String sqlDelReservas = "DELETE FROM reservations WHERE parking_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlDelReservas)) {
                ps.setString(1, id);
                ps.executeUpdate();
            }

            String sqlDel = "DELETE FROM parkings WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlDel)) {
                ps.setString(1, id);
                return ps.executeUpdate() > 0;
            }

        } catch (SQLException e) {
            System.err.println("Error eliminando parking: " + e.getMessage());
            return false;
        }
    }

    private String generarIdParking() {
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(id FROM 2) AS INTEGER)), 0) + 1 " +
                     "FROM parkings WHERE id LIKE 'P%'";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            if (rs.next()) {
                int siguiente = rs.getInt(1);
                return String.format("P%03d", siguiente);
            }
        } catch (SQLException e) {
            System.err.println("Error generando ID: " + e.getMessage());
        }
        return "P" + System.currentTimeMillis();
    }

    // ==================== TICKETS (BOLETAS DE ESTANCIA) ====================

    /**
     * Calcula el tiempo de estancia y la tarifa de una reserva sin tocar la base de datos.
     * Reglas:
     *  - minutos de estancia = tiempo real transcurrido desde la creacion de la reserva.
     *  - minutos facturables = max(estancia real, tiempo contratado en la reserva).
     *  - subtotal = tarifa por hora del vehiculo x horas facturables.
     */
    public Ticket estimarTicket(String reservationId) {
        Reservation r = buscarReserva(reservationId);
        if (r == null) {
            return null;
        }
        return calcularTicket(r, System.currentTimeMillis());
    }

    /**
     * Emite la boleta de una reserva: la calcula, la registra, cierra la reserva
     * y libera el espacio ocupado.
     */
    public synchronized Ticket crearTicket(String reservationId, String username)
            throws TicketException {
        if (reservationId == null || reservationId.isBlank()) {
            throw new TicketException(TicketException.Motivo.NO_ENCONTRADA,
                    "Falta el identificador de la reserva");
        }

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // Bloquea la reserva para que dos boletas no se emitan a la vez
            Reservation r = buscarReserva(conn, reservationId, true);
            if (r == null) {
                conn.rollback();
                throw new TicketException(TicketException.Motivo.NO_ENCONTRADA,
                        "La reserva " + reservationId + " no existe");
            }

            if (username != null && !username.isBlank() && !username.equals(r.getUsername())) {
                conn.rollback();
                throw new TicketException(TicketException.Motivo.SIN_PERMISO,
                        "La reserva pertenece a otro usuario");
            }

            if (existeTicket(conn, reservationId)) {
                conn.rollback();
                throw new TicketException(TicketException.Motivo.YA_EMITIDA,
                        "La reserva " + reservationId + " ya tiene una boleta emitida");
            }

            if (!"ACTIVA".equals(r.getEstado())) {
                conn.rollback();
                throw new TicketException(TicketException.Motivo.NO_ACTIVA,
                        "La reserva " + reservationId + " ya no esta activa");
            }

            Parking p = buscarParking(conn, r.getParkingId());
            if (p == null) {
                conn.rollback();
                throw new TicketException(TicketException.Motivo.PARKING_NO_ENCONTRADO,
                        "El parking de la reserva no existe");
            }

            long salida = System.currentTimeMillis();
            Ticket t = calcularTicket(r, salida);
            t.setId(generarIdTicket());
            t.setEstado("PAGADA");

            String sqlInsert = "INSERT INTO tickets " +
                "(id, reservation_id, parking_id, username, placa, tipo_vehiculo, " +
                " espacio_asignado, entrada, salida, minutos_estancia, " +
                " minutos_facturables, tarifa_hora, subtotal, estado) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                ps.setString(1, t.getId());
                ps.setString(2, t.getReservationId());
                ps.setString(3, t.getParkingId());
                ps.setString(4, t.getUsername());
                ps.setString(5, t.getPlaca());
                ps.setString(6, t.getTipoVehiculo());
                ps.setString(7, t.getEspacioAsignado());
                ps.setLong(8, t.getEntrada());
                ps.setLong(9, t.getSalida());
                ps.setInt(10, t.getMinutosEstancia());
                ps.setInt(11, t.getMinutosFacturables());
                ps.setDouble(12, t.getTarifaHora());
                ps.setDouble(13, t.getSubtotal());
                ps.setString(14, t.getEstado());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE reservations SET estado = 'FINALIZADA' WHERE id = ?")) {
                ps.setString(1, reservationId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE parkings SET espacios_ocupados = GREATEST(espacios_ocupados - 1, 0) " +
                    "WHERE id = ?")) {
                ps.setString(1, t.getParkingId());
                ps.executeUpdate();
            }

            conn.commit();
            return t;

        } catch (SQLException e) {
            System.err.println("Error creando ticket: " + e.getMessage());
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) {}
            return null;
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {}
        }
    }

    /** Calcula los valores de la boleta a partir de la reserva y la hora de salida. */
    private Ticket calcularTicket(Reservation r, long salida) {
        Parking p = buscarParking(r.getParkingId());

        Ticket t = new Ticket();
        t.setReservationId(r.getId());
        t.setParkingId(r.getParkingId());
        t.setParkingNombre(p != null ? p.getNombre() : r.getParkingId());
        t.setParkingDireccion(p != null ? p.getDireccion() : "");
        t.setUsername(r.getUsername());
        t.setPlaca(r.getPlaca());
        t.setTipoVehiculo(r.getTipoVehiculo());
        t.setEspacioAsignado(r.getEspacioAsignado());
        t.setEntrada(r.getTimestamp());
        t.setSalida(salida);

        int minutosEstancia = (int) Math.max(0L, (salida - r.getTimestamp()) / 60000L);
        int minutosContratados = r.getHoras() * 60 + r.getMinutos();
        int minutosFacturables = Math.max(minutosEstancia, minutosContratados);

        double tarifa = p != null ? p.getTarifaPorTipo(r.getTipoVehiculo()) : 0.0;
        double horasFacturables = minutosFacturables / 60.0;
        double subtotal = Math.round(tarifa * horasFacturables * 100.0) / 100.0;

        t.setMinutosEstancia(minutosEstancia);
        t.setMinutosFacturables(minutosFacturables);
        t.setTarifaHora(tarifa);
        t.setSubtotal(subtotal);
        return t;
    }

    private boolean existeTicket(Connection conn, String reservationId) throws SQLException {
        String sql = "SELECT 1 FROM tickets WHERE reservation_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String generarIdTicket() {
        return String.format("T%d%03d", System.currentTimeMillis(),
                (int) (Math.random() * 1000));
    }

    public List<Ticket> listarTickets(String username) {
        List<Ticket> lista = new ArrayList<>();
        String sql = "SELECT t.*, p.nombre AS parking_nombre, p.direccion AS parking_direccion " +
                     "FROM tickets t LEFT JOIN parkings p ON p.id = t.parking_id " +
                     (username != null && !username.isBlank() ? "WHERE t.username = ? " : "") +
                     "ORDER BY t.salida DESC";

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (username != null && !username.isBlank()) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, username);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            lista.add(mapearTicket(rs));
                        }
                    }
                }
            } else {
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(sql)) {
                    while (rs.next()) {
                        lista.add(mapearTicket(rs));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error listando tickets: " + e.getMessage());
        }
        return lista;
    }

    private Ticket mapearTicket(ResultSet rs) throws SQLException {
        Ticket t = new Ticket();
        t.setId(rs.getString("id"));
        t.setReservationId(rs.getString("reservation_id"));
        t.setParkingId(rs.getString("parking_id"));
        t.setParkingNombre(rs.getString("parking_nombre"));
        t.setParkingDireccion(rs.getString("parking_direccion"));
        t.setUsername(rs.getString("username"));
        t.setPlaca(rs.getString("placa"));
        t.setTipoVehiculo(rs.getString("tipo_vehiculo"));
        t.setEspacioAsignado(rs.getString("espacio_asignado"));
        t.setEntrada(rs.getLong("entrada"));
        t.setSalida(rs.getLong("salida"));
        t.setMinutosEstancia(rs.getInt("minutos_estancia"));
        t.setMinutosFacturables(rs.getInt("minutos_facturables"));
        t.setTarifaHora(rs.getDouble("tarifa_hora"));
        t.setSubtotal(rs.getDouble("subtotal"));
        t.setEstado(rs.getString("estado"));
        return t;
    }

    // ==================== RESERVAS ====================
    public synchronized Reservation crearReserva(String parkingId, String username,
                                                  String placa, String tipoVehiculo,
                                                  int horas, int minutos) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            Parking p = buscarParking(parkingId);
            if (p == null || p.getEspaciosDisponibles() <= 0) {
                conn.rollback();
                return null;
            }

            if (!tipoVehiculo.equals("CARRO") &&
                !tipoVehiculo.equals("MOTO") &&
                !tipoVehiculo.equals("CAMIONETA")) {
                tipoVehiculo = "CARRO";
            }

            double tarifa = p.getTarifaPorTipo(tipoVehiculo);
            double horasDecimales = horas + (minutos / 60.0);
            double costo = tarifa * horasDecimales;
            costo = Math.round(costo * 100.0) / 100.0;

            String id = "R" + System.currentTimeMillis();
            String espacio = generarEspacio(p);

            String sqlInsert = "INSERT INTO reservations " +
                "(id, parking_id, username, placa, tipo_vehiculo, horas, minutos, " +
                " espacio_asignado, costo_total, timestamp, estado) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                ps.setString(1, id);
                ps.setString(2, parkingId);
                ps.setString(3, username);
                ps.setString(4, placa);
                ps.setString(5, tipoVehiculo);
                ps.setInt(6, horas);
                ps.setInt(7, minutos);
                ps.setString(8, espacio);
                ps.setDouble(9, costo);
                ps.setLong(10, System.currentTimeMillis());
                ps.setString(11, "ACTIVA");
                ps.executeUpdate();
            }

            String sqlUpdate = "UPDATE parkings SET espacios_ocupados = espacios_ocupados + 1 WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
                ps.setString(1, parkingId);
                ps.executeUpdate();
            }

            conn.commit();

            return new Reservation(id, parkingId, username, placa,
                                    tipoVehiculo, horas, minutos, espacio, costo);

        } catch (SQLException e) {
            System.err.println("Error creando reserva: " + e.getMessage());
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) {}
            return null;
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {}
        }
    }

    private String generarEspacio(Parking p) {
        int numero = p.getEspaciosOcupados() + 1;
        char letra = (char) ('A' + (numero - 1) / 10);
        int num = ((numero - 1) % 10) + 1;
        return String.format("%c%02d", letra, num);
    }

    public List<Reservation> reservasDeUsuario(String username) {
        List<Reservation> lista = new ArrayList<>();
        String sql = "SELECT * FROM reservations WHERE username = ? ORDER BY timestamp DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearReserva(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error listando reservas: " + e.getMessage());
        }
        return lista;
    }

    public List<Reservation> todasLasReservas() {
        List<Reservation> lista = new ArrayList<>();
        String sql = "SELECT * FROM reservations ORDER BY timestamp DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                lista.add(mapearReserva(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error listando reservas: " + e.getMessage());
        }
        return lista;
    }

    private Reservation mapearReserva(ResultSet rs) throws SQLException {
        Reservation r = new Reservation(
            rs.getString("id"),
            rs.getString("parking_id"),
            rs.getString("username"),
            rs.getString("placa"),
            rs.getString("tipo_vehiculo"),
            rs.getInt("horas"),
            rs.getInt("minutos"),
            rs.getString("espacio_asignado"),
            rs.getDouble("costo_total")
        );
        r.setTimestamp(rs.getLong("timestamp"));
        r.setEstado(rs.getString("estado"));
        return r;
    }

    // ==================== HELPERS DE CONSULTA ====================
    public Reservation buscarReserva(String id) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return buscarReserva(conn, id, false);
        } catch (SQLException e) {
            System.err.println("Error buscando reserva: " + e.getMessage());
            return null;
        }
    }

    /**
     * @param bloquear true usa FOR UPDATE para bloquear la fila dentro de una transaccion.
     */
    private Reservation buscarReserva(Connection conn, String id, boolean bloquear) throws SQLException {
        String sql = "SELECT * FROM reservations WHERE id = ?" + (bloquear ? " FOR UPDATE" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapearReserva(rs);
                }
            }
        }
        return null;
    }

    private Parking buscarParking(Connection conn, String id) throws SQLException {
        String sql = "SELECT * FROM parkings WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapearParking(rs);
                }
            }
        }
        return null;
    }
}