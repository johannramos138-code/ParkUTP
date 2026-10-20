-- ============================================================
-- ParkUTP - Datos de prueba
-- Ejecutar sobre la base de datos 'parkutp' despues de crear el schema:
--   psql -U postgres -d parkutp -f backend/sql/seed.sql
--
-- NOTA: las contrasenas se guardan en texto plano porque el backend
-- (ParkingManager.login) compara el valor directamente con la BD.
-- ============================================================

-- ------------------------------------------------------------
-- USUARIOS (autenticacion)
-- ------------------------------------------------------------
INSERT INTO users (username, password, role) VALUES
    ('admin', 'admin123', 'ADMIN'),
    ('juan',  '1234',     'USER')
ON CONFLICT (username) DO UPDATE
    SET password = EXCLUDED.password,
        role     = EXCLUDED.role;

-- ------------------------------------------------------------
-- PARKINGS DE EJEMPLO
-- ------------------------------------------------------------
INSERT INTO parkings (id, nombre, direccion, espacios_totales, espacios_ocupados,
                      tarifa_hora, tarifa_moto, tarifa_camioneta) VALUES
    ('P001', 'Parking Central',   'Av.-principal 123',       50, 0, 5.00, 2.00, 6.00),
    ('P002', 'Parking Universidad','Jr. Universidad 456',     30, 0, 4.00, 2.00, 6.00),
    ('P003', 'Parking Norte',      'Av. Los Pinos 789',       20, 0, 3.50, 2.00, 6.00)
ON CONFLICT (id) DO UPDATE
    SET nombre            = EXCLUDED.nombre,
        direccion         = EXCLUDED.direccion,
        espacios_totales  = EXCLUDED.espacios_totales,
        tarifa_hora       = EXCLUDED.tarifa_hora,
        tarifa_moto       = EXCLUDED.tarifa_moto,
        tarifa_camioneta  = EXCLUDED.tarifa_camioneta;
