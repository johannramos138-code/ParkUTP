package com.parkutp.model;

public class Parking {
    private String id;
    private String nombre;
    private String direccion;
    private int espaciosTotales;
    private int espaciosOcupados;
    private double tarifaHora;
    private double tarifaMoto;
    private double tarifaCamioneta;

    public Parking() {}

    public Parking(String id, String nombre, String direccion,
                   int espaciosTotales, double tarifaHora,
                   double tarifaMoto, double tarifaCamioneta) {
        this.id = id;
        this.nombre = nombre;
        this.direccion = direccion;
        this.espaciosTotales = espaciosTotales;
        this.espaciosOcupados = 0;
        this.tarifaHora = tarifaHora;
        this.tarifaMoto = tarifaMoto;
        this.tarifaCamioneta = tarifaCamioneta;
    }

    public int getEspaciosDisponibles() {
        return espaciosTotales - espaciosOcupados;
    }

    public double getTarifaPorTipo(String tipo) {
        switch (tipo) {
            case "MOTO":      return tarifaMoto;
            case "CAMIONETA": return tarifaCamioneta;
            default:          return tarifaHora;
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String n) { this.nombre = n; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String d) { this.direccion = d; }
    public int getEspaciosTotales() { return espaciosTotales; }
    public void setEspaciosTotales(int e) { this.espaciosTotales = e; }
    public int getEspaciosOcupados() { return espaciosOcupados; }
    public void setEspaciosOcupados(int e) { this.espaciosOcupados = e; }
    public double getTarifaHora() { return tarifaHora; }
    public void setTarifaHora(double t) { this.tarifaHora = t; }
    public double getTarifaMoto() { return tarifaMoto; }
    public void setTarifaMoto(double t) { this.tarifaMoto = t; }
    public double getTarifaCamioneta() { return tarifaCamioneta; }
    public void setTarifaCamioneta(double t) { this.tarifaCamioneta = t; }
}