package com.parkutp.service;

import com.parkutp.model.Parking;
import com.parkutp.model.Reservation;
import com.parkutp.model.User;
import com.parkutp.util.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ParkingManager {

    private String ultimoError;

    public String getUltimoError() {
        return ultimoError;
    }

    // ==================== USUARIOS ====================
    public User login(String username, String password) {
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
        } catch (SQLException e) {
            System.err.println("Error en login: " + e.getMessage());
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

    // ==================== RESERVAS ====================
    public synchronized Reservation crearReserva(String parkingId, String username,
                                                  String placa, String tipoVehiculo,
                                                  int horas, int minutos) {
        Connection conn = null;
        ultimoError = null;

        if (placa == null || placa.trim().isEmpty()) {
            ultimoError = "La placa es obligatoria";
            return null;
        }

        placa = placa.trim().toUpperCase().replaceAll("\\s+", "");

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            String sqlPlacaActiva = "SELECT COUNT(*) FROM reservations " +
                                    "WHERE UPPER(REPLACE(COALESCE(placa, ''), ' ', '')) = ? " +
                                    "AND estado = 'ACTIVA'";
            try (PreparedStatement ps = conn.prepareStatement(sqlPlacaActiva)) {
                ps.setString(1, placa);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        conn.rollback();
                        ultimoError = "El vehiculo con placa " + placa + " ya tiene una reserva activa";
                        return null;
                    }
                }
            }

            Parking p = buscarParking(parkingId);
            if (p == null || p.getEspaciosDisponibles() <= 0) {
                conn.rollback();
                ultimoError = "Sin espacios disponibles";
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
            ultimoError = "No se pudo crear la reserva";
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
        r.setMetodoPago(rs.getString("metodo_pago"));
        return r;
    }

    // ==================== CHECK-OUT ====================
    public boolean registrarSalida(String idReserva, String metodoPago) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Buscar la reserva y verificar que esté ACTIVA
            String sqlSelect = "SELECT parking_id, estado FROM reservations WHERE id = ?";
            String parkingId = null;
            
            try (PreparedStatement ps = conn.prepareStatement(sqlSelect)) {
                ps.setString(1, idReserva);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        if (!"ACTIVA".equals(rs.getString("estado"))) {
                            return false; // Ya fue finalizada
                        }
                        parkingId = rs.getString("parking_id");
                    } else {
                        return false; // No existe la reserva
                    }
                }
            }

            // 2. Cambiar estado de la reserva a FINALIZADA y guardar metodo_pago
            String sqlUpdateRes = "UPDATE reservations SET estado = 'FINALIZADA', metodo_pago = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlUpdateRes)) {
                ps.setString(1, metodoPago);
                ps.setString(2, idReserva);
                ps.executeUpdate();
            }

            // 3. Restar 1 espacio ocupado al parking (liberar espacio)
            String sqlUpdatePark = "UPDATE parkings SET espacios_ocupados = espacios_ocupados - 1 " +
                                   "WHERE id = ? AND espacios_ocupados > 0";
            try (PreparedStatement ps = conn.prepareStatement(sqlUpdatePark)) {
                ps.setString(1, parkingId);
                ps.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            System.err.println("Error en checkout: " + e.getMessage());
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) {}
            return false;
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {}
        }
    }
}