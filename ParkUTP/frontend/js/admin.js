const username = localStorage.getItem('username');
if (!username) window.location.href = '/index.html';

document.getElementById('welcome').textContent = username;

let parkingEditandoId = null;
let reservaSeleccionada = null;

// ==================== CARGAR STATS ====================
async function cargarStats() {
    const parkings = await API.getParkings();
    const reservas = await API.getReservas();

    const totalEspacios = parkings.reduce((sum, p) => sum + p.espaciosTotales, 0);
    const totalOcupados = parkings.reduce((sum, p) => sum + p.espaciosOcupados, 0);
    const totalDisponibles = totalEspacios - totalOcupados;
    const totalIngresos = reservas.reduce((sum, r) => sum + r.costoTotal, 0);

    document.getElementById('statsGrid').innerHTML = `
        <div class="stat-card">
            <div class="stat-icon blue">${ICONOS.parking}</div>
            <div class="stat-info">
                <h3>${parkings.length}</h3>
                <p>Parkings</p>
            </div>
        </div>
        <div class="stat-card">
            <div class="stat-icon green">${ICONOS.disponible}</div>
            <div class="stat-info">
                <h3>${totalDisponibles}</h3>
                <p>Espacios disponibles</p>
            </div>
        </div>
        <div class="stat-card">
            <div class="stat-icon orange">${ICONOS.ocupado}</div>
            <div class="stat-info">
                <h3>${totalOcupados}</h3>
                <p>Espacios ocupados</p>
            </div>
        </div>
        <div class="stat-card">
            <div class="stat-icon red">${ICONOS.dinero}</div>
            <div class="stat-info">
                <h3>S/. ${totalIngresos.toFixed(2)}</h3>
                <p>Ingresos totales</p>
            </div>
        </div>
    `;
}

// ==================== CARGAR PARKINGS ====================
async function cargarParkings() {
    const parkings = await API.getParkings();
    const cont = document.getElementById('listaParkings');

    if (parkings.length === 0) {
        cont.innerHTML = `
            <div class="empty-state">
                ${ICONOS.vacio}
                <p>No hay parkings. Crea el primero</p>
            </div>
        `;
        return;
    }

    cont.innerHTML = parkings.map(p => {
        const disponibles = p.espaciosTotales - p.espaciosOcupados;
        const porcentaje = (p.espaciosOcupados / p.espaciosTotales) * 100;

        let claseBarra = '';
        if (porcentaje > 80) claseBarra = 'alto';
        else if (porcentaje > 50) claseBarra = 'medio';

        return `
            <div class="card">
                <h3>${p.nombre}</h3>
                <p class="direccion">${ICONOS.ubicacion} ${p.direccion}</p>

                <div class="tarifas">
                    <div class="tarifa-item">
                        <div class="tipo">Carro</div>
                        <div class="precio">S/. ${p.tarifaHora.toFixed(2)}</div>
                    </div>
                    <div class="tarifa-item">
                        <div class="tipo">Moto</div>
                        <div class="precio">S/. ${p.tarifaMoto.toFixed(2)}</div>
                    </div>
                    <div class="tarifa-item">
                        <div class="tipo">Camioneta</div>
                        <div class="precio">S/. ${p.tarifaCamioneta.toFixed(2)}</div>
                    </div>
                </div>

                <div class="ocupacion-bar">
                    <div class="ocupacion-fill ${claseBarra}" style="width: ${porcentaje}%"></div>
                </div>

                <div class="ocupacion-info">
                    <span class="disponibles">${disponibles} disponibles</span>
                    <span class="ocupados">${p.espaciosOcupados}/${p.espaciosTotales}</span>
                </div>

                <div class="card-actions">
                    <button class="btn-edit" onclick='abrirModalEditar(${JSON.stringify(p).replace(/'/g, "&apos;")})'>Editar</button>
                    <button class="btn-delete" onclick="confirmarEliminar('${p.id}', '${p.nombre}')">Eliminar</button>
                </div>
            </div>
        `;
    }).join('');
}

// ==================== CARGAR RESERVAS ====================
async function cargarReservas() {
    const reservas = await API.getReservas();
    const tbody = document.querySelector('#tablaReservas tbody');

    if (reservas.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="10" style="text-align: center; padding: 40px; color: #64748b;">
                    No hay reservas registradas
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = reservas.map(r => {
        const tiempo = r.minutos > 0
            ? `${r.horas}h ${r.minutos}min`
            : `${r.horas}h`;

        // Si está ACTIVA mostramos el botón, si no, mostramos un guion
        const accionBtn = r.estado === 'ACTIVA' 
            ? `<button class="btn-primary" style="padding: 6px 12px; font-size: 0.8rem;" onclick="darSalida('${r.id}')">Dar Salida</button>`
            : '-';

        return `
            <tr>
                <td>${r.id}</td>
                <td>${r.parkingId}</td>
                <td>${r.username}</td>
                <td>${r.placa}</td>
                <td><span class="tipo-badge">${r.tipoVehiculo}</span></td>
                <td>${tiempo}</td>
                <td>${r.espacioAsignado || '-'}</td>
                <td>S/. ${r.costoTotal.toFixed(2)}</td>
                <td><span class="estado-badge ${r.estado.toLowerCase()}">${r.estado}</span></td>
                <td>${accionBtn}</td>
            </tr>
        `;
    }).join('');
}

// ==================== MODAL CREAR/EDITAR PARKING ====================
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
        mostrarToast('Nombre y direccion son obligatorios', 'warning');
        return;
    }
    if (datos.espaciosTotales < 1) {
        mostrarToast('Debe haber al menos 1 espacio', 'warning');
        return;
    }
    if (datos.tarifaHora < 0 || datos.tarifaMoto < 0 || datos.tarifaCamioneta < 0) {
        mostrarToast('Las tarifas no pueden ser negativas', 'warning');
        return;
    }

    let resp;
    if (parkingEditandoId) {
        resp = await API.actualizarParking(parkingEditandoId, datos);
    } else {
        resp = await API.crearParking(datos);
    }

    if (resp.status === 'OK') {
        mostrarToast(parkingEditandoId ? 'Parking actualizado' : 'Parking creado', 'success');
        cerrarModalParking();
        cargarParkings();
        cargarStats();
    } else {
        mostrarToast(resp.message || 'No se pudo guardar', 'error');
    }
}

// ==================== ELIMINAR ====================
async function confirmarEliminar(id, nombre) {
    if (!confirm(`Eliminar el parking "${nombre}"?\n\nEsta accion no se puede deshacer.`)) {
        return;
    }

    const resp = await API.eliminarParking(id);
    if (resp.status === 'OK') {
        mostrarToast('Parking eliminado', 'success');
        cargarParkings();
        cargarReservas();
        cargarStats();
    } else {
        mostrarToast(resp.message || 'No se pudo eliminar', 'error');
    }
}

// ==================== MODAL COBRO / BOLETA ====================
function darSalida(idReserva) {
    const fila = event.target.closest('tr');
    const celdas = fila.querySelectorAll('td');
    
    reservaSeleccionada = idReserva;
    
    // Llenar la boleta visual
    document.getElementById('bTicket').textContent = idReserva;
    document.getElementById('bPlaca').textContent = celdas[3].textContent;
    document.getElementById('bTipo').textContent = celdas[4].textContent;
    document.getElementById('bTiempo').textContent = celdas[5].textContent;
    document.getElementById('bEspacio').textContent = celdas[6].textContent;
    // Limpiar el "S/." para solo mostrar el número
    document.getElementById('bTotal').textContent = celdas[7].textContent.replace('S/. ', '');

    // Mostrar modal
    document.getElementById('modalPago').classList.remove('hidden');
}

function cerrarModalPago() {
    document.getElementById('modalPago').classList.add('hidden');
    reservaSeleccionada = null;
}

async function procesarPago() {
    if (!reservaSeleccionada) return;
    const metodo = document.getElementById('selectMetodoPago').value;
    
    const resp = await API.checkout(reservaSeleccionada, metodo);
    
    if (resp.status === 'OK') {
        mostrarToast(`Pago procesado vía ${metodo}. Espacio liberado.`, 'success');
        cerrarModalPago();
        cargarReservas();
        cargarParkings();
        cargarStats();
    } else {
        mostrarToast(resp.message || 'Error al procesar', 'error');
    }
}

// ==================== VALIDADOR QR ====================
async function validarQR() {
    const ticketInput = document.getElementById('ticketInput');
    const resultado = document.getElementById('resultadoValidacion');
    const ticketId = ticketInput.value.trim();

    if (!ticketId) {
        resultado.className = 'resultado-validacion error';
        resultado.textContent = 'Ingresa o escanea el ID del ticket.';
        return;
    }

    const tickets = await API.getTickets();
    const ticket = tickets.find(item => item.id === ticketId);

    if (!ticket) {
        resultado.className = 'resultado-validacion error';
        resultado.textContent = 'Ticket no encontrado.';
        return;
    }

    const estado = (ticket.estado || '').toUpperCase();
    const estaPagado = estado === 'PAGADA' ||
                       estado === 'PAGADO' ||
                       (estado === 'FINALIZADA' && ticket.metodoPago);

    if (estaPagado) {
        resultado.className = 'resultado-validacion success';
        resultado.textContent = `Ticket valido: el vehiculo ${ticket.placa || ''} puede salir.`;
    } else {
        resultado.className = 'resultado-validacion error';
        resultado.textContent = 'El ticket existe, pero el vehiculo no esta pagado.';
    }
}

// ==================== INIT ====================
cargarStats();
cargarParkings();
cargarReservas();