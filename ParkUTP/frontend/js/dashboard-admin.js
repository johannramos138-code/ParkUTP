const ADMIN_DASHBOARD_KEY = 'parkutp.admin.dashboard';
const DIAS_REPORTE_POR_DEFECTO = 30;
let actualizacionDashboardEnCurso = false;
let notificacionesAdmin = [];

function leerEstadoDashboard() {
    const guardado = localStorage.getItem(ADMIN_DASHBOARD_KEY);
    if (!guardado) {
        return { reservasVistas: null, ticketsVistos: null, notificaciones: [] };
    }

    try {
        const estado = JSON.parse(guardado);
        return {
            reservasVistas: Array.isArray(estado.reservasVistas) ? estado.reservasVistas : null,
            ticketsVistos: Array.isArray(estado.ticketsVistos) ? estado.ticketsVistos : null,
            notificaciones: Array.isArray(estado.notificaciones) ? estado.notificaciones : []
        };
    } catch (error) {
        console.error('No se pudo leer el historial local de notificaciones:', error);
        mostrarToast('No se pudo leer el historial de notificaciones guardado en este navegador.', 'error');
        return { reservasVistas: null, ticketsVistos: null, notificaciones: [] };
    }
}

function guardarEstadoDashboard(reservasVistas, ticketsVistos) {
    localStorage.setItem(ADMIN_DASHBOARD_KEY, JSON.stringify({
        reservasVistas: reservasVistas.slice(0, 500),
        ticketsVistos: ticketsVistos.slice(0, 500),
        notificaciones: notificacionesAdmin.slice(0, 30)
    }));
}

function formatearImporte(valor) {
    return Number(valor || 0).toFixed(2);
}

function claveFechaLocal(valor) {
    const fecha = new Date(valor);
    return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
}

function renderizarNotificaciones() {
    const contenedor = document.getElementById('listaNotificaciones');
    const noLeidas = notificacionesAdmin.filter(n => !n.leida).length;
    const boton = document.getElementById('marcarNotificacionesLeidas');
    boton.textContent = noLeidas ? `Marcar todas como leidas (${noLeidas})` : 'Todas leidas';
    boton.disabled = noLeidas === 0;

    if (!notificacionesAdmin.length) {
        contenedor.innerHTML = '<p class="notification-empty">Aun no hay notificaciones nuevas.</p>';
        return;
    }

    contenedor.innerHTML = notificacionesAdmin.map(n => `
        <article class="notification-item ${n.leida ? '' : 'unread'}">
            <span class="notification-dot" aria-hidden="true"></span>
            <div>
                <h3>${escaparHtml(n.titulo)}</h3>
                <p>${escaparHtml(n.detalle)}</p>
                <time datetime="${new Date(n.fecha).toISOString()}">${new Date(n.fecha).toLocaleString('es-PE')}</time>
            </div>
        </article>
    `).join('');
}

function crearNotificacion(titulo, detalle, id) {
    notificacionesAdmin.unshift({
        id,
        titulo,
        detalle,
        fecha: Date.now(),
        leida: false
    });
    notificacionesAdmin = notificacionesAdmin.slice(0, 30);
}

function renderizarGraficoLineas(contenedor, datos, formatoValor) {
    const ancho = 720;
    const alto = 230;
    const margen = { arriba: 16, derecha: 16, abajo: 42, izquierda: 54 };
    const anchoGrafico = ancho - margen.izquierda - margen.derecha;
    const altoGrafico = alto - margen.arriba - margen.abajo;
    const maximo = Math.max(1, ...datos.map(d => d.valor));
    const puntos = datos.map((d, i) => {
        const x = margen.izquierda + (datos.length === 1 ? anchoGrafico / 2 : i * anchoGrafico / (datos.length - 1));
        const y = margen.arriba + altoGrafico - d.valor / maximo * altoGrafico;
        return { x, y, dato: d };
    });
    const linea = puntos.map(p => `${p.x},${p.y}`).join(' ');
    const salto = Math.max(1, Math.ceil(datos.length / 6));
    const etiquetas = puntos.map((p, i) => {
        if (i % salto !== 0 && i !== datos.length - 1) return '';
        return `<text x="${p.x}" y="${alto - 12}" text-anchor="middle">${escaparHtml(p.dato.etiqueta)}</text>`;
    }).join('');

    contenedor.innerHTML = `
        <svg class="line-chart" viewBox="0 0 ${ancho} ${alto}" role="img" aria-label="Grafico de tendencia">
            <line x1="${margen.izquierda}" y1="${margen.arriba}" x2="${margen.izquierda}" y2="${alto - margen.abajo}" class="chart-axis"/>
            <line x1="${margen.izquierda}" y1="${alto - margen.abajo}" x2="${ancho - margen.derecha}" y2="${alto - margen.abajo}" class="chart-axis"/>
            <polyline points="${linea}" class="chart-line"/>
            ${puntos.map(p => `<circle cx="${p.x}" cy="${p.y}" r="4" class="chart-point"><title>${escaparHtml(p.dato.etiqueta)}: ${escaparHtml(formatoValor(p.dato.valor))}</title></circle>`).join('')}
            ${etiquetas}
        </svg>
    `;
}

function renderizarReportes(parkings, reservas, tickets) {
    const dias = Number(document.getElementById('periodoReporte').value) || DIAS_REPORTE_POR_DEFECTO;
    const hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    const inicio = new Date(hoy);
    inicio.setDate(inicio.getDate() - dias + 1);

    const series = Array.from({ length: dias }, (_, indice) => {
        const fecha = new Date(inicio);
        fecha.setDate(fecha.getDate() + indice);
        return {
            clave: `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`,
            etiqueta: fecha.toLocaleDateString('es-PE', { day: '2-digit', month: '2-digit' }),
            valorIngresos: 0,
            valorReservas: 0
        };
    });
    const porFecha = new Map(series.map(d => [d.clave, d]));
    const inicioMs = inicio.getTime();
    const finMs = hoy.getTime() + 86400000;
    const reservasPeriodo = reservas.filter(r => r.timestamp >= inicioMs && r.timestamp < finMs);
    const ticketsPeriodo = tickets.filter(t => t.salida >= inicioMs && t.salida < finMs);

    reservasPeriodo.forEach(r => {
        const d = porFecha.get(claveFechaLocal(r.timestamp));
        if (d) d.valorReservas++;
    });
    ticketsPeriodo.forEach(t => {
        const d = porFecha.get(claveFechaLocal(t.salida));
        if (d) d.valorIngresos += Number(t.subtotal) || 0;
    });

    const ingresos = ticketsPeriodo.reduce((suma, t) => suma + (Number(t.subtotal) || 0), 0);
    const ocupados = parkings.reduce((suma, p) => suma + Number(p.espaciosOcupados || 0), 0);
    const capacidad = parkings.reduce((suma, p) => suma + Number(p.espaciosTotales || 0), 0);
    const porcentajeOcupacion = capacidad ? ocupados / capacidad * 100 : 0;
    document.getElementById('statsGrid').innerHTML = `
        <article class="stat-card"><div class="stat-icon blue"><span>P</span></div><div class="stat-info"><h3>${parkings.length}</h3><p>Parkings</p></div></article>
        <article class="stat-card"><div class="stat-icon orange"><span>E</span></div><div class="stat-info"><h3>${ocupados} / ${capacidad}</h3><p>Espacios ocupados</p></div></article>
        <article class="stat-card"><div class="stat-icon green"><span>R</span></div><div class="stat-info"><h3>${reservas.filter(r => r.estado === 'ACTIVA').length}</h3><p>Reservas activas</p></div></article>
        <article class="stat-card"><div class="stat-icon red"><span>B</span></div><div class="stat-info"><h3>${tickets.length}</h3><p>Boletas emitidas</p></div></article>
    `;
    document.getElementById('resumenReportes').innerHTML = `
        <article class="report-metric"><span>Ingresos por boletas</span><strong>S/. ${formatearImporte(ingresos)}</strong></article>
        <article class="report-metric"><span>Reservas del periodo</span><strong>${reservasPeriodo.length}</strong></article>
        <article class="report-metric"><span>Ocupacion actual</span><strong>${porcentajeOcupacion.toFixed(1)}%</strong></article>
    `;

    renderizarGraficoLineas(
        document.getElementById('graficoIngresos'),
        series.map(d => ({ etiqueta: d.etiqueta, valor: d.valorIngresos })),
        valor => `S/. ${formatearImporte(valor)}`
    );
    renderizarGraficoLineas(
        document.getElementById('graficoReservas'),
        series.map(d => ({ etiqueta: d.etiqueta, valor: d.valorReservas })),
        valor => `${valor} reservas`
    );

    const ocupacion = document.getElementById('graficoOcupacion');
    ocupacion.innerHTML = parkings.length ? parkings.map(p => {
        const total = Number(p.espaciosTotales) || 0;
        const actual = Number(p.espaciosOcupados) || 0;
        const porcentaje = total ? Math.min(100, actual / total * 100) : 0;
        return `
            <div class="occupancy-row">
                <div><strong>${escaparHtml(p.nombre)}</strong><span>${actual} / ${total} (${porcentaje.toFixed(1)}%)</span></div>
                <div class="occupancy-track"><span style="width:${porcentaje}%"></span></div>
            </div>
        `;
    }).join('') : '<p class="notification-empty">No hay parkings para reportar.</p>';
}

async function actualizarDashboardAdmin() {
    if (actualizacionDashboardEnCurso) return;
    actualizacionDashboardEnCurso = true;
    try {
        const [parkings, reservas, tickets] = await Promise.all([
            API.getParkings(),
            API.getReservas(),
            API.getTickets()
        ]);
        if (![parkings, reservas, tickets].every(Array.isArray)) {
            throw new Error('La API devolvio datos invalidos para el panel administrativo.');
        }

        const estado = leerEstadoDashboard();
        const idsReservas = reservas.map(r => r.id);
        const idsTickets = tickets.map(t => t.id);
        if (estado.reservasVistas !== null) {
            const anteriores = new Set(estado.reservasVistas);
                reservas.filter(r => !anteriores.has(r.id)).forEach(r => {
                crearNotificacion(
                    'Nueva reserva',
                    `${r.username} reservo el espacio ${r.espacioAsignado || '-'} en ${r.parkingId}.`,
                    `reserva-${r.id}`
                );
            });
        }
        if (estado.ticketsVistos !== null) {
            const anteriores = new Set(estado.ticketsVistos);
            tickets.filter(t => !anteriores.has(t.id)).forEach(t => {
                crearNotificacion(
                    'Nueva boleta emitida',
                    `Boleta ${t.id} de ${t.username}: S/. ${formatearImporte(t.subtotal)}.`,
                    `ticket-${t.id}`
                );
            });
        }

        const duplicadas = new Set();
        notificacionesAdmin = notificacionesAdmin.filter(n => {
            if (duplicadas.has(n.id)) return false;
            duplicadas.add(n.id);
            return true;
        }).slice(0, 30);
        guardarEstadoDashboard(idsReservas, idsTickets);
        renderizarNotificaciones();
        renderizarReportes(parkings, reservas, tickets);
    } catch (error) {
        console.error('No se pudo actualizar el panel administrativo:', error);
        document.getElementById('listaNotificaciones').innerHTML =
            '<p class="notification-empty error">No se pudieron cargar las notificaciones y reportes. Revisa la conexion con el servidor.</p>';
        mostrarToast('No se pudo actualizar el panel administrativo.', 'error');
    } finally {
        actualizacionDashboardEnCurso = false;
    }
}

document.getElementById('marcarNotificacionesLeidas').addEventListener('click', () => {
    notificacionesAdmin = notificacionesAdmin.map(n => ({ ...n, leida: true }));
    const estado = leerEstadoDashboard();
    guardarEstadoDashboard(estado.reservasVistas || [], estado.ticketsVistos || []);
    renderizarNotificaciones();
});
document.getElementById('periodoReporte').addEventListener('change', actualizarDashboardAdmin);

notificacionesAdmin = leerEstadoDashboard().notificaciones;
renderizarNotificaciones();
inicializarMapaEspacios();
actualizarDashboardAdmin();
window.setInterval(actualizarDashboardAdmin, 20000);
