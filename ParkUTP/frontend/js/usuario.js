const username = localStorage.getItem('username');
if (!username) window.location.href = '/index.html';

document.getElementById('welcome').textContent = `Hola, ${username}`;

let parkingActual = null;
let nombreParkingActual = '';
let tarifasActuales = { CARRO: 0, MOTO: 0, CAMIONETA: 0 };
let misReservasPrevias = [];

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
    misReservasPrevias = reservas;

    document.querySelector('#tablaReservas tbody').innerHTML = reservas.map(r => {
        const tiempo = r.minutos > 0
            ? `${r.horas}h ${r.minutos}min`
            : `${r.horas}h`;

        return `
            <tr>
                <td>${r.id}</td>
                <td>${r.parkingId}</td>
                <td>${r.placa}</td>
                <td>${r.tipoVehiculo}</td>
                <td>${tiempo}</td>
                <td>${r.espacioAsignado || '-'}</td>
                <td>S/. ${r.costoTotal.toFixed(2)}</td>
                <td>${r.estado}</td>
            </tr>
        `;
    }).join('');
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
    cargarPlacasAnteriores();
    document.getElementById('modal').classList.remove('hidden');

    actualizarPrecio();
}

function cargarPlacasAnteriores() {
    const placaSelect = document.getElementById('placaSelect');
    const placasUnicas = [...new Set(
        misReservasPrevias
            .map(reserva => reserva.placa)
            .filter(placa => placa && placa.trim())
    )];

    placaSelect.innerHTML = '<option value="">Selecciona una placa</option>';

    placasUnicas.forEach(placa => {
        const option = document.createElement('option');
        option.value = placa;
        option.textContent = placa;
        placaSelect.appendChild(option);
    });

    const nuevaOption = document.createElement('option');
    nuevaOption.value = 'NUEVA';
    nuevaOption.textContent = '+ Registrar nueva placa';
    placaSelect.appendChild(nuevaOption);

    document.getElementById('placaNuevaContainer').classList.add('hidden');
    document.getElementById('placa').value = '';
}

function manejarCambioPlaca() {
    const placaSelect = document.getElementById('placaSelect');
    const container = document.getElementById('placaNuevaContainer');
    const placaInput = document.getElementById('placa');

    container.classList.toggle('hidden', placaSelect.value !== 'NUEVA');

    if (placaSelect.value !== 'NUEVA') {
        placaInput.value = '';
    } else {
        placaInput.focus();
    }
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
    document.getElementById('placaSelect').value = '';
    document.getElementById('placa').value = '';
    document.getElementById('placaNuevaContainer').classList.add('hidden');
    document.getElementById('horas').value = 1;
    document.getElementById('minutos').value = 0;
    document.getElementById('tipoVehiculo').value = 'CARRO';
    document.getElementById('precioPreview').textContent = '0.00';
}

// ==================== CONFIRMAR RESERVA ====================
async function confirmarReserva() {
    const placaSelect = document.getElementById('placaSelect');
    const placaSeleccionada = placaSelect.value === 'NUEVA'
        ? document.getElementById('placa').value.trim()
        : placaSelect.value.trim();
    const placa = placaSeleccionada;
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
        cerrarModal();
        verBoletaEmitida(resp.reservation);
        cargarParkings();
        cargarReservas();
    } else {
        alert('Error: ' + resp.message);
    }
}

// ==================== BOLETA QR ====================
function verBoletaEmitida(reservation) {
    const ticketId = reservation.id;
    document.getElementById('boletaTicket').textContent = ticketId;

    const contenedorQR = document.getElementById('contenedorQR');
    contenedorQR.innerHTML = '';

    const imagenQR = document.createElement('img');
    imagenQR.src = `https://api.qrserver.com/v1/create-qr-code/?size=120x120&data=${encodeURIComponent(ticketId)}`;
    imagenQR.alt = `Codigo QR del ticket ${ticketId}`;
    imagenQR.width = 120;
    imagenQR.height = 120;
    imagenQR.style.display = 'block';
    imagenQR.style.margin = '12px auto';

    contenedorQR.appendChild(imagenQR);
    document.getElementById('modalBoleta').classList.remove('hidden');
}

function cerrarBoletaEmitida() {
    document.getElementById('modalBoleta').classList.add('hidden');
}

// ==================== INIT ====================
document.getElementById('tipoVehiculo').addEventListener('change', actualizarPrecio);
document.getElementById('horas').addEventListener('input', actualizarPrecio);
document.getElementById('minutos').addEventListener('input', actualizarPrecio);

cargarParkings();
cargarReservas();