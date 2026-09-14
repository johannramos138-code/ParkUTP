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
    }
};

function logout() {
    localStorage.clear();
    window.location.href = '/index.html';
}