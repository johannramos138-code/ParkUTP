package com.parkutp.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    
    private static final String URL = "jdbc:postgresql://localhost:5432/parkutp";
    private static final String USER = "postgres";
    private static final String PASSWORD = "1234";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
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
