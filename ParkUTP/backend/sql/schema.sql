-- ============================================================
-- ParkUTP - Esquema de la base de datos
-- Ejecutar sobre la base de datos 'parkutp':
--   createdb -U postgres parkutp
--   psql -U postgres -d parkutp -f backend/sql/schema.sql
-- ============================================================

-- ============================================================
-- TABLA: users
-- ============================================================
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ADMIN'))
);

-- ============================================================
-- TABLA: parkings
-- ============================================================
CREATE TABLE parkings (
    id VARCHAR(10) PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    direccion VARCHAR(200) NOT NULL,
    espacios_totales INT NOT NULL,
    espacios_ocupados INT NOT NULL DEFAULT 0,
    tarifa_hora NUMERIC(10,2) NOT NULL,
    tarifa_moto NUMERIC(10,2) DEFAULT 2.00,
    tarifa_camioneta NUMERIC(10,2) DEFAULT 6.00
);

-- ============================================================
-- TABLA: reservations
-- ============================================================
CREATE TABLE reservations (
    id VARCHAR(30) PRIMARY KEY,
    parking_id VARCHAR(10) REFERENCES parkings(id) ON DELETE CASCADE,
    username VARCHAR(50) REFERENCES users(username),
    placa VARCHAR(20) NOT NULL,
    tipo_vehiculo VARCHAR(20) NOT NULL DEFAULT 'CARRO',
    horas INT NOT NULL,
    minutos INT NOT NULL DEFAULT 0,
    espacio_asignado VARCHAR(10),
    costo_total NUMERIC(10,2) NOT NULL,
    timestamp BIGINT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA'
);

-- ============================================================
-- TABLA: tickets (boleta de estancia)
-- Debe ir despues de 'reservations' por la clave foranea.
-- ============================================================
CREATE TABLE tickets (
    id VARCHAR(30) PRIMARY KEY,
    reservation_id VARCHAR(30) NOT NULL UNIQUE REFERENCES reservations(id) ON DELETE CASCADE,
    parking_id VARCHAR(10) NOT NULL REFERENCES parkings(id) ON DELETE CASCADE,
    username VARCHAR(50) NOT NULL REFERENCES users(username),
    placa VARCHAR(20) NOT NULL,
    tipo_vehiculo VARCHAR(20) NOT NULL,
    espacio_asignado VARCHAR(10),
    entrada BIGINT NOT NULL,
    salida BIGINT NOT NULL,
    minutos_estancia INT NOT NULL,
    minutos_facturables INT NOT NULL,
    tarifa_hora NUMERIC(10,2) NOT NULL,
    subtotal NUMERIC(10,2) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PAGADA'
);
