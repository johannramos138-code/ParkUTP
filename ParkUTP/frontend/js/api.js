const API = {
    async login(username, password) {
        const r = await fetch('/api/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        return r.json();
    },

    async getParkings() {
        const r = await fetch('/api/parkings');
        return r.json();
    },

    async crearParking(parking) {
        const r = await fetch('/api/parkings', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(parking)
        });
        return r.json();
    },

    async actualizarParking(id, parking) {
        const r = await fetch('/api/parkings?id=' + id, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(parking)
        });
        return r.json();
    },

    async eliminarParking(id) {
        const r = await fetch('/api/parkings?id=' + id, {
            method: 'DELETE'
        });
        return r.json();
    },

    async crearReserva(parkingId, username, placa, tipoVehiculo, horas, minutos) {
        const r = await fetch('/api/reservations', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                parkingId,
                username,
                placa,
                tipoVehiculo,
                horas,
                minutos
            })
        });
        return r.json();
    },

    async getReservas(username) {
        const url = username
            ? `/api/reservations?username=${username}`
            : '/api/reservations';
        const r = await fetch(url);
        return r.json();
    },

    // ==================== TICKETS / BOLETAS ====================
    /** Calcula el tiempo de estancia y la tarifa de una reserva sin emitir la boleta. */
    async estimarTicket(reservationId) {
        const r = await fetch('/api/tickets/estimado?reserva=' + encodeURIComponent(reservationId));
        return r.json();
    },

    /** Emite la boleta: cierra la reserva, libera el espacio y devuelve el ticket. */
    async crearTicket(reservationId, username) {
        const r = await fetch('/api/tickets', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ reservationId, username })
        });
        return r.json();
    },

    async getTickets(username) {
        const url = username
            ? `/api/tickets?username=${encodeURIComponent(username)}`
            : '/api/tickets';
        const r = await fetch(url);
        return r.json();
    }
};

function logout() {
    localStorage.clear();
    window.location.href = '/index.html';
}