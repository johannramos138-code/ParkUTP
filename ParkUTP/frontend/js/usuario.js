const username = localStorage.getItem('username');
if (!username) window.location.href = '/index.html';

document.getElementById('welcome').textContent = `Hola, ${username}`;

let parkingActual = null;
let nombreParkingActual = '';
let tarifasActuales = { CARRO: 0, MOTO: 0, CAMIONETA: 0 };

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
    } else {
        alert('Error: ' + resp.message);
    }
}

// ==================== INIT ====================
cargarParkings();
cargarReservas();