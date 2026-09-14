package com.parkutp.model;

public class Reservation {
    private String id;
    private String parkingId;
    private String username;
    private String placa;
    private String tipoVehiculo;
    private int horas;
    private int minutos;
    private String espacioAsignado;
    private double costoTotal;
    private long timestamp;
    private String estado;

    public Reservation() {}

    public Reservation(String id, String parkingId, String username,
                       String placa, String tipoVehiculo,
                       int horas, int minutos, String espacioAsignado,
                       double costoTotal) {
        this.id = id;
        this.parkingId = parkingId;
        this.username = username;
        this.placa = placa;
        this.tipoVehiculo = tipoVehiculo;
        this.horas = horas;
        this.minutos = minutos;
        this.espacioAsignado = espacioAsignado;
        this.costoTotal = costoTotal;
        this.timestamp = System.currentTimeMillis();
        this.estado = "ACTIVA";
    }

    public String getId() { return id; }
    public void setId(String i) { this.id = i; }
    public String getParkingId() { return parkingId; }
    public void setParkingId(String p) { this.parkingId = p; }
    public String getUsername() { return username; }
    public void setUsername(String u) { this.username = u; }
    public String getPlaca() { return placa; }
    public void setPlaca(String p) { this.placa = p; }
    public String getTipoVehiculo() { return tipoVehiculo; }
    public void setTipoVehiculo(String t) { this.tipoVehiculo = t; }
    public int getHoras() { return horas; }
    public void setHoras(int h) { this.horas = h; }
    public int getMinutos() { return minutos; }
    public void setMinutos(int m) { this.minutos = m; }
    public String getEspacioAsignado() { return espacioAsignado; }
    public void setEspacioAsignado(String e) { this.espacioAsignado = e; }
    public double getCostoTotal() { return costoTotal; }
    public void setCostoTotal(double c) { this.costoTotal = c; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long t) { this.timestamp = t; }
    public String getEstado() { return estado; }
    public void setEstado(String e) { this.estado = e; }
}