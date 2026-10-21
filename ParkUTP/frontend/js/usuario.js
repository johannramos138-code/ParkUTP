const username = localStorage.getItem('username');
if (!username) window.location.href = '/index.html';

document.getElementById('welcome').textContent = `Hola, ${username}`;

let parkingActual = null;
let nombreParkingActual = '';
let tarifasActuales = { CARRO: 0, MOTO: 0, CAMIONETA: 0 };
let reservaParaBoleta = null;

// ==================== CARGAR PARKINGS ====================
async function cargarParkings() {
    const parkings = await API.getParkings();
    const cont = document.getElementById('listaParkings');

    cont.innerHTML = parkings.map(p => {
        const disponibles = p.espaciosTotales - p.espaciosOcupados;
        const sinEspacios = disponibles <= 0;

        return `
            <div class="card">
                <h3>${p.nombre}</h3>
                <p>${p.direccion}</p>
                <p>Carro: S/. ${p.tarifaHora.toFixed(2)}/h</p>
                <p>Moto: S/. ${p.tarifaMoto.toFixed(2)}/h</p>
                <p>Camioneta: S/. ${p.tarifaCamioneta.toFixed(2)}/h</p>
                <p class="disponibles">Disponibles: ${disponibles}</p>
                <button ${sinEspacios ? 'disabled' : ''}
                        onclick='abrirModal(${JSON.stringify(p)})'>
                    ${sinEspacios ? 'Sin espacios' : 'Reservar'}
                </button>
            </div>
        `;
    }).join('');
}

// ==================== CARGAR RESERVAS ====================
async function cargarReservas() {
    const reservas = await API.getReservas(username);
    const tbody = document.querySelector('#tablaReservas tbody');

    if (!reservas.length) {
        tbody.innerHTML = '<tr><td colspan="9" class="celda-vacia">Aun no tienes reservas</td></tr>';
        return;
    }

    tbody.innerHTML = reservas.map((r, i) => {
        const tiempo = r.minutos > 0
            ? `${r.horas}h ${r.minutos}min`
            : `${r.horas}h`;

        const estado = r.estado === 'ACTIVA'
            ? '<span class="estado-badge activa">ACTIVA</span>'
            : '<span class="estado-badge finalizada">' + escaparHtml(r.estado) + '</span>';

        const accion = r.estado === 'ACTIVA'
            ? '<button class="btn-ticket" data-accion="boleta" data-index="' + i + '">Emitir boleta</button>'
            : '<span class="sin-acciones">-</span>';

        return `
            <tr>
                <td>${escaparHtml(r.id)}</td>
                <td>${escaparHtml(r.parkingId)}</td>
                <td>${escaparHtml(r.placa)}</td>
                <td>${escaparHtml(r.tipoVehiculo)}</td>
                <td>${tiempo}</td>
                <td>${escaparHtml(r.espacioAsignado || '-')}</td>
                <td>S/. ${r.costoTotal.toFixed(2)}</td>
                <td>${estado}</td>
                <td>${accion}</td>
            </tr>
        `;
    }).join('');

    // Delegacion de eventos: los datos viajan por data-index, no por onclick en linea
    tbody.querySelectorAll('button[data-accion="boleta"]').forEach(btn => {
        btn.addEventListener('click', () => {
            prepararBoleta(reservas[Number(btn.dataset.index)].id);
        });
    });
}

// ==================== BOLETAS ====================
async function cargarTickets() {
    const tickets = await API.getTickets(username);
    const tbody = document.querySelector('#tablaTickets tbody');

    if (!tickets.length) {
        tbody.innerHTML = '<tr><td colspan="8" class="celda-vacia">Aun no has emitido boletas</td></tr>';
        return;
    }

    tbody.innerHTML = tickets.map((t, i) => `
        <tr>
            <td>${escaparHtml(t.id)}</td>
            <td>${formatearFecha(t.salida)}</td>
            <td>${escaparHtml(t.parkingNombre || t.parkingId)}</td>
            <td>${escaparHtml(t.placa)}</td>
            <td>${formatearDuracion(t.minutosEstancia)}</td>
            <td>S/. ${formatearMoneda(t.subtotal)}</td>
            <td><span class="estado-badge finalizada">${escaparHtml(t.estado)}</span></td>
            <td class="acciones">
                <button class="btn-ticket" data-accion="ver" data-index="${i}">Ver</button>
                <button class="btn-ticket secondary" data-accion="descargar" data-index="${i}">.txt</button>
                <button class="btn-ticket secondary" data-accion="imprimir" data-index="${i}">Imprimir</button>
            </td>
        </tr>
    `).join('');

    tbody.querySelectorAll('button[data-accion]').forEach(btn => {
        btn.addEventListener('click', () => {
            const ticket = tickets[Number(btn.dataset.index)];
            switch (btn.dataset.accion) {
                case 'ver':       abrirModalBoleta(ticket); break;
                case 'descargar': descargarBoletaTexto(ticket); break;
                case 'imprimir':  imprimirBoleta(ticket); break;
            }
        });
    });
}

/** Calcula el tiempo de estancia y la tarifa antes de emitir la boleta. */
async function prepararBoleta(reservationId) {
    const resp = await API.estimarTicket(reservationId);

    if (resp.status !== 'OK') {
        mostrarToast(resp.message || 'No se pudo calcular el tiempo de estancia', 'error');
        return;
    }

    const est = resp.estimado;
    reservaParaBoleta = reservationId;

    document.getElementById('confirmarBoletaInfo').textContent =
        `Reserva ${est.reservationId} - ${est.placa} - ${est.parkingNombre}`;

    document.getElementById('confirmarBoletaDetalle').innerHTML = `
        <tr><th>Tiempo de estancia</th><td>${formatearDuracion(est.minutosEstancia)}</td></tr>
        <tr><th>Tiempo facturable</th><td>${formatearDuracion(est.minutosFacturables)}</td></tr>
        <tr><th>Tarifa por hora</th><td>S/. ${formatearMoneda(est.tarifaHora)}</td></tr>
        <tr><th>Total a pagar</th><td>S/. ${formatearMoneda(est.subtotal)}</td></tr>
    `;

    document.getElementById('modalConfirmarBoleta').classList.remove('hidden');
}

function cerrarModalConfirmarBoleta() {
    document.getElementById('modalConfirmarBoleta').classList.add('hidden');
    reservaParaBoleta = null;
}

/** Emite la boleta, cierra la reserva y muestra la vista previa descargable. */
async function confirmarEmisionBoleta() {
    if (!reservaParaBoleta) return;

    const resp = await API.crearTicket(reservaParaBoleta, username);

    if (resp.status === 'OK') {
        cerrarModalConfirmarBoleta();
        abrirModalBoleta(resp.ticket);
        mostrarToast('Boleta ' + resp.ticket.id + ' emitida');
        cargarParkings();
        cargarReservas();
        cargarTickets();
        if (window.actualizarMapaEspacios) window.actualizarMapaEspacios();
    } else {
        mostrarToast(resp.message || 'No se pudo emitir la boleta', 'error');
    }
}

// ==================== MODAL ====================
function abrirModal(parking) {
    parkingActual = parking.id;
    nombreParkingActual = parking.nombre;

    tarifasActuales = {
        CARRO: parking.tarifaHora,
        MOTO: parking.tarifaMoto,
        CAMIONETA: parking.tarifaCamioneta
    };

    document.getElementById('modalParking').textContent = parking.nombre;
    document.getElementById('modal').classList.remove('hidden');

    actualizarPrecio();
}

function actualizarPrecio() {
    const tipo = document.getElementById('tipoVehiculo').value;
    const horas = parseInt(document.getElementById('horas').value) || 0;
    const minutos = parseInt(document.getElementById('minutos').value) || 0;

    const tarifa = tarifasActuales[tipo] || 0;
    const horasDecimales = horas + (minutos / 60);
    const total = tarifa * horasDecimales;

    document.getElementById('precioPreview').textContent = total.toFixed(2);
}

function cerrarModal() {
    document.getElementById('modal').classList.add('hidden');
    document.getElementById('placa').value = '';
    document.getElementById('horas').value = 1;
    document.getElementById('minutos').value = 0;
    document.getElementById('tipoVehiculo').value = 'CARRO';
    document.getElementById('precioPreview').textContent = '0.00';
}

// ==================== CONFIRMAR RESERVA ====================
async function confirmarReserva() {
    const placa = document.getElementById('placa').value.trim();
    const tipoVehiculo = document.getElementById('tipoVehiculo').value;
    const horas = parseInt(document.getElementById('horas').value) || 0;
    const minutos = parseInt(document.getElementById('minutos').value) || 0;

    if (!placa) {
        alert('Ingresa la placa del vehiculo');
        return;
    }

    if (horas === 0 && minutos === 0) {
        alert('Ingresa al menos 1 minuto de tiempo');
        return;
    }

    const resp = await API.crearReserva(
        parkingActual,
        username,
        placa,
        tipoVehiculo,
        horas,
        minutos
    );

    if (resp.status === 'OK') {
        alert(
            'Reserva creada correctamente\n' +
            'Espacio: ' + resp.reservation.espacioAsignado + '\n' +
            'Total: S/. ' + resp.reservation.costoTotal.toFixed(2)
        );
        cerrarModal();
        cargarParkings();
        cargarReservas();
        if (window.actualizarMapaEspacios) window.actualizarMapaEspacios();
    } else {
        alert('Error: ' + resp.message);
    }
}

// ==================== INIT ====================
cargarParkings();
cargarReservas();
cargarTickets();
inicializarMapaEspacios();