package com.parkutp.model;

/**
 * Boleta de estancia de una reserva.
 * Reune los datos de la reserva mas el calculo de tiempo de estancia y tarifa.
 */
public class Ticket {
    private String id;
    private String reservationId;
    private String parkingId;
    private String parkingNombre;
    private String parkingDireccion;
    private String username;
    private String placa;
    private String tipoVehiculo;
    private String espacioAsignado;
    private long entrada;
    private long salida;
    private int minutosEstancia;
    private int minutosFacturables;
    private double tarifaHora;
    private double subtotal;
    private String estado;

    public Ticket() {}

    public String getId() { return id; }
    public void setId(String i) { this.id = i; }
    public String getReservationId() { return reservationId; }
    public void setReservationId(String r) { this.reservationId = r; }
    public String getParkingId() { return parkingId; }
    public void setParkingId(String p) { this.parkingId = p; }
    public String getParkingNombre() { return parkingNombre; }
    public void setParkingNombre(String p) { this.parkingNombre = p; }
    public String getParkingDireccion() { return parkingDireccion; }
    public void setParkingDireccion(String d) { this.parkingDireccion = d; }
    public String getUsername() { return username; }
    public void setUsername(String u) { this.username = u; }
    public String getPlaca() { return placa; }
    public void setPlaca(String p) { this.placa = p; }
    public String getTipoVehiculo() { return tipoVehiculo; }
    public void setTipoVehiculo(String t) { this.tipoVehiculo = t; }
    public String getEspacioAsignado() { return espacioAsignado; }
    public void setEspacioAsignado(String e) { this.espacioAsignado = e; }
    public long getEntrada() { return entrada; }
    public void setEntrada(long e) { this.entrada = e; }
    public long getSalida() { return salida; }
    public void setSalida(long s) { this.salida = s; }
    public int getMinutosEstancia() { return minutosEstancia; }
    public void setMinutosEstancia(int m) { this.minutosEstancia = m; }
    public int getMinutosFacturables() { return minutosFacturables; }
    public void setMinutosFacturables(int m) { this.minutosFacturables = m; }
    public double getTarifaHora() { return tarifaHora; }
    public void setTarifaHora(double t) { this.tarifaHora = t; }
    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double s) { this.subtotal = s; }
    public String getEstado() { return estado; }
    public void setEstado(String e) { this.estado = e; }
}
