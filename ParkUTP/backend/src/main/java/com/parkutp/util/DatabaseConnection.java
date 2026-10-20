package com.parkutp.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String URL_POR_DEFECTO = "jdbc:postgresql://localhost:5432/parkutp";
    private static final String USER_POR_DEFECTO = "postgres";
    private static final String PASSWORD_POR_DEFECTO = "rodrigohuanca.13";

    // Se pueden sobrescribir con -Dparkutp.db.url=... o las variables de entorno
    // PARKUTP_DB_URL / PARKUTP_DB_USER / PARKUTP_DB_PASSWORD (util para pruebas).
    private static final String URL = propiedad("parkutp.db.url", "PARKUTP_DB_URL", URL_POR_DEFECTO);
    private static final String USER = propiedad("parkutp.db.user", "PARKUTP_DB_USER", USER_POR_DEFECTO);
    private static final String PASSWORD = propiedad("parkutp.db.password", "PARKUTP_DB_PASSWORD", PASSWORD_POR_DEFECTO);

    /** Valor de una propiedad del sistema o de una variable de entorno, con valor por defecto. */
    private static String propiedad(String sistema, String entorno, String porDefecto) {
        String valor = System.getProperty(sistema);
        if (valor == null || valor.isBlank()) {
            valor = System.getenv(entorno);
        }
        return (valor == null || valor.isBlank()) ? porDefecto : valor;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** Abre una conexion a otra base del mismo servidor (p. ej. 'postgres'). */
    public static Connection getConnection(String database) throws SQLException {
        return DriverManager.getConnection(cambiarBase(URL, database), USER, PASSWORD);
    }

    /** Sustituye el nombre de la base de datos en una URL jdbc:postgresql. */
    public static String cambiarBase(String url, String database) {
        int ultimaBarra = url.lastIndexOf('/');
        if (ultimaBarra < 0) {
            return url;
        }
        return url.substring(0, ultimaBarra + 1) + database;
    }

    public static String getUrl() {
        return URL;
    }

    public static String getUser() {
        return USER;
    }

    public static String getPassword() {
        return PASSWORD;
    }

    /** Nombre de la base de datos configurada (ultimo segmento de la URL). */
    public static String getNombreBase() {
        int ultimaBarra = URL.lastIndexOf('/');
        if (ultimaBarra < 0) {
            return "";
        }
        return URL.substring(ultimaBarra + 1);
    }

    // Test de conexión
    public static void main(String[] args) {
        try (Connection conn = getConnection()) {
            System.out.println(" Conectado a PostgreSQL");
            System.out.println("   Base de datos: " + conn.getCatalog());
        } catch (SQLException e) {
            System.err.println(" Error: " + e.getMessage());
        }
    }
}
