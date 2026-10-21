package com.parkutp.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Inicializacion automatica de la base de datos al arrancar el servidor.
 *
 * Al ejecutar {@code HttpServerApp.main()} se lee la carpeta de scripts SQL
 * ({@code backend/sql/}) y se aplican sus sentencias de forma idempotente:
 *
 * <ul>
 *   <li>Si la base de datos no existe, se crea.</li>
 *   <li>El esquema se aplica con {@code CREATE TABLE IF NOT EXISTS} (y tambien
 *       {@code CREATE INDEX IF NOT EXISTS}, {@code CREATE SCHEMA IF NOT EXISTS}...),
 *       de modo que arrancarlo varias veces no produce errores.</li>
 *   <li>Los datos de prueba ({@code seed.sql}) se aplican con su propio
 *       {@code ON CONFLICT}, por lo que tampoco se duplican.</li>
 * </ul>
 *
 * La busqueda de la carpeta SQL prueba varias rutas, de forma que funciona
 * tanto desde la raiz del proyecto como desde {@code backend/}.
 */
public class DatabaseInitializer {

    /** Rutas candidatas donde buscar los scripts SQL (relativas al directorio de trabajo). */
    private static final String[] CARPETAS_SQL = {
        "backend/sql",
        "sql",
        "../sql",
        "../backend/sql"
    };

    /** Scripts que se aplican, en este orden. */
    private static final String[] SCRIPTS = {
        "schema.sql",
        "seed.sql"
    };

    // ==================== RESULTADO ====================

    /** Resumen de la inicializacion. */
    public static class Resultado {
        private final List<String> aplicadas = new ArrayList<>();
        private final List<String> errores = new ArrayList<>();
        private final List<String> scripts = new ArrayList<>();
        private boolean baseCreada;
        private boolean baseExistente = true;

        /** Sentencias ejecutadas correctamente. */
        public List<String> getAplicadas() { return aplicadas; }
        /** Sentencias que fallaron (con su mensaje de error). */
        public List<String> getErrores() { return errores; }
        /** Scripts aplicados correctamente. */
        public List<String> getScripts() { return scripts; }
        /** true si la base de datos no existia y se creo. */
        public boolean isBaseCreada() { return baseCreada; }
        /** false si no se pudo conectar con la base de datos. */
        public boolean isBaseExistente() { return baseExistente; }
        /** true si no hubo ningun error. */
        public boolean isExitoso() { return errores.isEmpty() && baseExistente; }
    }

    // ==================== API PUBLICA ====================

    /**
     * Inicializa la base de datos configurada: crea la base si falta y aplica
     * los scripts SQL de forma idempotente.
     * @return el resumen de lo ejecutado
     */
    public static Resultado inicializar() {
        Resultado resultado = new Resultado();

        Path carpeta = resolverCarpetaSql();
        if (carpeta == null) {
            System.err.println("No se encontro la carpeta con los scripts SQL.");
            System.err.println("  Se esperaba " + String.join(" o ", CARPETAS_SQL) + "/schema.sql");
            System.err.println("  Arranca el servidor desde la raiz del proyecto o define -Dparkutp.sql.dir=<ruta>.");
            resultado.baseExistente = false;
            return resultado;
        }

        System.out.println("Inicializando base de datos '" + DatabaseConnection.getNombreBase() + "'");
        System.out.println("  Scripts SQL: " + carpeta.toAbsolutePath());

        // 1. La base de datos debe existir
        try (Connection conn = DatabaseConnection.getConnection()) {
            System.out.println("  Base de datos: ya existia (" + conn.getCatalog() + ")");
        } catch (SQLException e) {
            if (!crearBaseDeDatos()) {
                resultado.baseExistente = false;
                resultado.errores.add("No se pudo conectar ni crear la base de datos: " + e.getMessage());
                return resultado;
            }
            resultado.baseCreada = true;
            System.out.println("  Base de datos: creada");
        }

        // 2. Aplicar los scripts
        try (Connection conn = DatabaseConnection.getConnection()) {
            for (String script : SCRIPTS) {
                Path archivo = carpeta.resolve(script);
                if (!Files.exists(archivo)) {
                    System.out.println("  [" + (script.startsWith("schema") ? "OK  " : "SKIP") + "] "
                            + script + ": no existe, se omite");
                    continue;
                }
                aplicarScript(conn, archivo, script, resultado);
            }
        } catch (SQLException e) {
            resultado.errores.add("Error de conexion al aplicar los scripts: " + e.getMessage());
            return resultado;
        }

        if (resultado.getErrores().isEmpty()) {
            System.out.println("  Base de datos lista (" + resultado.getAplicadas().size()
                    + " sentencias aplicadas)");
        } else {
            System.err.println("  La base de datos quedo con " + resultado.getErrores().size()
                    + " error(es). Revisa el log del servidor.");
        }
        return resultado;
    }

    /**
     * Aplica los scripts SQL sobre una conexion ya abierta. Util para pruebas
     * automatizadas sobre una base de datos cualquiera.
     * @param conn        conexion destino
     * @param carpetaSql  carpeta que contiene schema.sql y seed.sql
     */
    public static Resultado inicializar(Connection conn, Path carpetaSql) {
        Resultado resultado = new Resultado();
        for (String script : SCRIPTS) {
            Path archivo = carpetaSql.resolve(script);
            if (Files.exists(archivo)) {
                aplicarScript(conn, archivo, script, resultado);
            }
        }
        return resultado;
    }

    // ==================== IMPLEMENTACION ====================

    /** Localiza la carpeta con los scripts SQL probando las rutas candidatas. */
    public static Path resolverCarpetaSql() {
        String configurada = System.getProperty("parkutp.sql.dir");
        if (configurada == null || configurada.isBlank()) {
            configurada = System.getenv("PARKUTP_SQL_DIR");
        }
        if (configurada != null && !configurada.isBlank()) {
            Path ruta = Paths.get(configurada);
            if (Files.isDirectory(ruta)) {
                return ruta;
            }
            System.err.println("La carpeta indicada en -Dparkutp.sql.dir no existe: " + ruta);
        }

        for (String carpeta : CARPETAS_SQL) {
            Path ruta = Paths.get(carpeta);
            if (Files.isDirectory(ruta)) {
                return ruta;
            }
        }

        // Ultimo recurso: la carpeta relativa a las clases compiladas
        // (backend/target/classes -> ../../sql -> backend/sql)
        try {
            Path clases = Paths.get(DatabaseInitializer.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path relativa = clases.resolve("../../sql").normalize();
            if (Files.isDirectory(relativa)) {
                return relativa;
            }
        } catch (Exception e) {
            // sin ruta por clase: se informara con el aviso de la carpeta no encontrada
        }
        return null;
    }

    /** Crea la base de datos configurada si todavia no existe. */
    private static boolean crearBaseDeDatos() {
        String nombre = DatabaseConnection.getNombreBase();
        if (nombre.isBlank()) {
            return false;
        }
        try (Connection conn = DatabaseConnection.getConnection("postgres");
             Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE DATABASE \"" + nombre + "\"");
            return true;
        } catch (SQLException e) {
            System.err.println("No se pudo crear la base '" + nombre + "': " + e.getMessage());
            return false;
        }
    }

    /** Lee un script, lo divide en sentencias y las aplica una a una. */
    private static void aplicarScript(Connection conn, Path archivo, String nombre, Resultado resultado) {
        String sql;
        try {
            sql = Files.readString(archivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            resultado.errores.add(nombre + ": no se pudo leer (" + e.getMessage() + ")");
            System.err.println("  [ERROR] " + nombre + ": no se pudo leer (" + e.getMessage() + ")");
            return;
        }

        List<String> sentencias = separarSentencias(sql);
        int aplicadas = 0;
        int fallidas = 0;

        for (String sentencia : sentencias) {
            String segura = hacerIdempotente(sentencia);
            try (Statement st = conn.createStatement()) {
                st.execute(segura);
                aplicadas++;
                resultado.aplicadas.add(nombre + ": " + resumir(segura));
            } catch (SQLException e) {
                fallidas++;
                String detalle = nombre + ": " + resumir(segura) + " -> " + e.getMessage();
                resultado.errores.add(detalle);
                System.err.println("  [ERROR] " + detalle);
            }
        }

        if (fallidas == 0) {
            System.out.println("  [OK  ] " + nombre + ": " + aplicadas + " sentencias aplicadas");
            resultado.scripts.add(nombre);
        } else {
            System.out.println("  [WARN] " + nombre + ": " + aplicadas + " aplicadas, " + fallidas + " con error");
        }
    }

    /**
     * Convierte una sentencia CREATE en su equivalente idempotente, para que
     * ejecutarla sobre una base ya inicializada no genere error.
     */
    public static String hacerIdempotente(String sentencia) {
        String s = sentencia.trim();
        String[] prefijos = {
            "CREATE TABLE",
            "CREATE UNIQUE TABLE",
            "CREATE INDEX",
            "CREATE UNIQUE INDEX",
            "CREATE SCHEMA",
            "CREATE SEQUENCE"
        };
        for (String prefijo : prefijos) {
            if (s.regionMatches(true, 0, prefijo, 0, prefijo.length())) {
                String resto = s.substring(prefijo.length());
                if (!resto.trim().regionMatches(true, 0, "IF NOT EXISTS", 0, "IF NOT EXISTS".length())) {
                    return prefijo + " IF NOT EXISTS" + resto;
                }
                return s;
            }
        }
        return s;
    }

    /**
     * Divide un script SQL en sentencias individuales, respetando los comentarios
     * (-- y /* *\/), las cadenas entre comillas simples, los identificadores entre
     * comillas dobles y los bloques delimitados por $$.
     */
    public static List<String> separarSentencias(String sql) {
        List<String> sentencias = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        int i = 0;
        int n = sql.length();

        while (i < n) {
            char c = sql.charAt(i);
            char siguiente = (i + 1 < n) ? sql.charAt(i + 1) : '\0';

            // Comentario de linea: -- hasta el fin de linea
            if (c == '-' && siguiente == '-') {
                while (i < n && sql.charAt(i) != '\n') {
                    i++;
                }
                actual.append(' ');
                continue;
            }

            // Comentario de bloque: /* ... */
            if (c == '/' && siguiente == '*') {
                i += 2;
                while (i + 1 < n && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    i++;
                }
                i += 2;
                actual.append(' ');
                continue;
            }

            // Bloque delimitado por $$ o $tag$
            if (c == '$') {
                String delimitador = leerDelimitadorDollar(sql, i);
                if (delimitador != null) {
                    int fin = sql.indexOf(delimitador, i + delimitador.length());
                    if (fin < 0) {
                        fin = n;
                    } else {
                        fin += delimitador.length();
                    }
                    actual.append(sql, i, fin);
                    i = fin;
                    continue;
                }
            }

            // Cadena entre comillas simples ('' escapa la comilla)
            if (c == '\'') {
                int j = i + 1;
                actual.append('\'');
                while (j < n) {
                    char actualChar = sql.charAt(j);
                    if (actualChar == '\'') {
                        if (j + 1 < n && sql.charAt(j + 1) == '\'') {
                            actual.append("''");
                            j += 2;
                            continue;
                        }
                        actual.append('\'');
                        j++;
                        break;
                    }
                    actual.append(actualChar);
                    j++;
                }
                i = j;
                continue;
            }

            // Identificador entre comillas dobles
            if (c == '"') {
                int j = sql.indexOf('"', i + 1);
                j = (j < 0) ? n : j + 1;
                actual.append(sql, i, j);
                i = j;
                continue;
            }

            // Separador de sentencias
            if (c == ';') {
                agregarSiNoVacia(sentencias, actual);
                actual.setLength(0);
                i++;
                continue;
            }

            actual.append(c);
            i++;
        }

        agregarSiNoVacia(sentencias, actual);
        return sentencias;
    }

    /** Devuelve el delimitador dollar-quoted que empieza en la posicion indicada, o null. */
    private static String leerDelimitadorDollar(String sql, int i) {
        if (i + 1 >= sql.length()) {
            return null;
        }
        char siguiente = sql.charAt(i + 1);
        // Delimitador vacio: $$ ... $$
        if (siguiente == '$') {
            return "$$";
        }
        // Delimitador con etiqueta: $tag$ ... $tag$
        if (!Character.isLetter(siguiente) && siguiente != '_') {
            return null;
        }
        int j = i + 1;
        while (j < sql.length()
                && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_')) {
            j++;
        }
        if (j < sql.length() && sql.charAt(j) == '$') {
            return sql.substring(i, j + 1);
        }
        return null;
    }

    private static void agregarSiNoVacia(List<String> sentencias, StringBuilder actual) {
        String texto = actual.toString().trim();
        if (!texto.isEmpty()) {
            sentencias.add(texto);
        }
    }

    /** Resume una sentencia para el log (una sola linea, sin saltos). */
    private static String resumir(String sentencia) {
        String plano = sentencia.replaceAll("\\s+", " ").trim();
        if (plano.length() > 90) {
            plano = plano.substring(0, 87) + "...";
        }
        return plano;
    }
}
