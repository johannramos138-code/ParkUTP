const username = localStorage.getItem('username');
if (!username) window.location.href = '/index.html';

document.getElementById('welcome').textContent = `Admin: ${username}`;

let parkingEditandoId = null;

// ==================== CARGAR PARKINGS ====================
async function cargarParkings() {
    const parkings = await API.getParkings();
    const cont = document.getElementById('listaParkings');

    cont.innerHTML = parkings.map(p => {
        const disponibles = p.espaciosTotales - p.espaciosOcupados;
        const porcentaje = (p.espaciosOcupados / p.espaciosTotales) * 100;

        return `
            <div class="card">
                <h3>${p.nombre}</h3>
                <p>${p.direccion}</p>
                <p>Ocupados: <span class="ocupados">${p.espaciosOcupados}</span> / ${p.espaciosTotales}</p>
                <p class="disponibles">Disponibles: ${disponibles}</p>
                <p>Carro: S/. ${p.tarifaHora.toFixed(2)}/h</p>
                <p>Moto: S/. ${p.tarifaMoto.toFixed(2)}/h</p>
                <p>Camioneta: S/. ${p.tarifaCamioneta.toFixed(2)}/h</p>
                <p>Ocupacion: ${porcentaje.toFixed(1)}%</p>
                <div class="card-actions">
                    <button class="btn-edit" onclick='abrirModalEditar(${JSON.stringify(p)})'>Editar</button>
                    <button class="btn-delete" onclick="confirmarEliminar('${p.id}', '${p.nombre}')">Eliminar</button>
                </div>
            </div>
        `;
    }).join('');
    if (window.actualizarMapaEspacios) window.actualizarMapaEspacios();
}

// ==================== CARGAR RESERVAS ====================
async function cargarReservas() {
    const reservas = await API.getReservas();

    document.querySelector('#tablaReservas tbody').innerHTML = reservas.map(r => {
        const tiempo = r.minutos > 0
            ? `${r.horas}h ${r.minutos}min`
            : `${r.horas}h`;

        return `
            <tr>
                <td>${r.id}</td>
                <td>${r.parkingId}</td>
                <td>${r.username}</td>
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

// ==================== MODAL NUEVO/EDITAR ====================
function abrirModalNuevo() {
    parkingEditandoId = null;
    document.getElementById('modalTitulo').textContent = 'Nuevo Parking';
    document.getElementById('pNombre').value = '';
    document.getElementById('pDireccion').value = '';
    document.getElementById('pEspacios').value = 20;
    document.getElementById('pTarifaHora').value = 5.00;
    document.getElementById('pTarifaMoto').value = 2.50;
    document.getElementById('pTarifaCamioneta').value = 6.00;
    document.getElementById('modalParking').classList.remove('hidden');
}

function abrirModalEditar(p) {
    parkingEditandoId = p.id;
    document.getElementById('modalTitulo').textContent = 'Editar Parking: ' + p.id;
    document.getElementById('pNombre').value = p.nombre;
    document.getElementById('pDireccion').value = p.direccion;
    document.getElementById('pEspacios').value = p.espaciosTotales;
    document.getElementById('pTarifaHora').value = p.tarifaHora;
    document.getElementById('pTarifaMoto').value = p.tarifaMoto;
    document.getElementById('pTarifaCamioneta').value = p.tarifaCamioneta;
    document.getElementById('modalParking').classList.remove('hidden');
}

function cerrarModalParking() {
    document.getElementById('modalParking').classList.add('hidden');
    parkingEditandoId = null;
}

async function guardarParking() {
    const datos = {
        nombre: document.getElementById('pNombre').value.trim(),
        direccion: document.getElementById('pDireccion').value.trim(),
        espaciosTotales: parseInt(document.getElementById('pEspacios').value),
        tarifaHora: parseFloat(document.getElementById('pTarifaHora').value),
        tarifaMoto: parseFloat(document.getElementById('pTarifaMoto').value),
        tarifaCamioneta: parseFloat(document.getElementById('pTarifaCamioneta').value)
    };

    if (!datos.nombre || !datos.direccion) {
        alert('Nombre y direccion son obligatorios');
        return;
    }
    if (datos.espaciosTotales < 1) {
        alert('Debe haber al menos 1 espacio');
        return;
    }
    if (datos.tarifaHora < 0 || datos.tarifaMoto < 0 || datos.tarifaCamioneta < 0) {
        alert('Las tarifas no pueden ser negativas');
        return;
    }

    let resp;
    if (parkingEditandoId) {
        resp = await API.actualizarParking(parkingEditandoId, datos);
    } else {
        resp = await API.crearParking(datos);
    }

    if (resp.status === 'OK') {
        alert(parkingEditandoId ? 'Parking actualizado' : 'Parking creado');
        cerrarModalParking();
        cargarParkings();
    } else {
        alert('Error: ' + (resp.message || 'No se pudo guardar'));
    }
}

// ==================== ELIMINAR ====================
async function confirmarEliminar(id, nombre) {
    if (!confirm('Eliminar el parking "' + nombre + '"?\n\nEsta accion no se puede deshacer.')) {
        return;
    }

    const resp = await API.eliminarParking(id);
    if (resp.status === 'OK') {
        alert('Parking eliminado');
        cargarParkings();
        cargarReservas();
    } else {
        alert('Error: ' + (resp.message || 'No se pudo eliminar'));
    }
}

// ==================== INIT ====================
cargarParkings();
cargarReservas();