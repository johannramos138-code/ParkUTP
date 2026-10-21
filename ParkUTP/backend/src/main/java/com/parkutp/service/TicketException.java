package com.parkutp.service;

/**
 * Error de dominio al emitir una boleta. El motivo permite al servidor HTTP
 * elegir el codigo de respuesta sin acoplarse a la logica de negocio.
 */
public class TicketException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Motivo {
        NO_ENCONTRADA,      // la reserva no existe
        YA_EMITIDA,         // la reserva ya tiene una boleta
        NO_ACTIVA,          // la reserva ya fue finalizada o cancelada
        SIN_PERMISO,        // la reserva pertenece a otro usuario
        PARKING_NO_ENCONTRADO
    }

    private final Motivo motivo;

    public TicketException(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
