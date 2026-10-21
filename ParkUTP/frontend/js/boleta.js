// ==================== BOLETAS DE PAGO ====================
// Genera la boleta de estancia de un ticket en texto plano (descargable)
// y en HTML (vista previa e impresion web).

const ANCHO_BOLETA = 40;

// Ticket que se esta visualizando en el modal (para los botones de la vista previa)
let boletaActual = null;

// ==================== FORMATO ====================

/** Timestamp epoch (ms) -> dd/mm/yyyy hh:mm:ss */
function formatearFecha(ts) {
    if (!ts) return '-';
    const d = new Date(ts);
    const p = n => String(n).padStart(2, '0');
    return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} ` +
           `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

/** Minutos -> "2 h 30 min" */
function formatearDuracion(minutos) {
    const m = Math.max(0, Math.round(minutos || 0));
    const h = Math.floor(m / 60);
    const rest = m % 60;
    if (h === 0) return `${rest} min`;
    if (rest === 0) return `${h} h`;
    return `${h} h ${rest} min`;
}

function formatearMoneda(monto) {
    return Number(monto || 0).toFixed(2);
}

// ==================== BOLETA EN TEXTO PLANO ====================

function _lineaSeparador(caracter = '=') {
    return caracter.repeat(ANCHO_BOLETA);
}

/** Linea "etiqueta ... valor" alineada al ancho de la boleta. */
function _lineaCampo(etiqueta, valor) {
    const izq = etiqueta.padEnd(16);
    const der = String(valor ?? '-');
    if (izq.length + der.length + 1 <= ANCHO_BOLETA) {
        return izq + ' '.repeat(ANCHO_BOLETA - izq.length - der.length) + der;
    }
    return izq + '\n' + ' '.repeat(16) + der;
}

/**
 * Genera el texto plano de la boleta (formato ticket, 40 columnas).
 * @returns {string}
 */
function generarTextoBoleta(ticket) {
    if (!ticket) return '';

    const lineas = [
        _lineaSeparador('='),
        '        PARKUTP - BOLETA DE PAGO',
        _lineaSeparador('='),
        _lineaCampo('Boleta Nro:', ticket.id || '-'),
        _lineaCampo('Emitida:', formatearFecha(ticket.salida)),
        _lineaSeparador('-'),
        _lineaCampo('Parking:', ticket.parkingNombre || ticket.parkingId || '-'),
        _lineaCampo('Direccion:', ticket.parkingDireccion || '-'),
        _lineaCampo('Espacio:', ticket.espacioAsignado || '-'),
        _lineaSeparador('-'),
        _lineaCampo('Usuario:', ticket.username || '-'),
        _lineaCampo('Placa:', ticket.placa || '-'),
        _lineaCampo('Vehiculo:', ticket.tipoVehiculo || '-'),
        _lineaSeparador('-'),
        _lineaCampo('Entrada:', formatearFecha(ticket.entrada)),
        _lineaCampo('Salida:', formatearFecha(ticket.salida)),
        _lineaCampo('Estancia:', formatearDuracion(ticket.minutosEstancia)),
        _lineaCampo('Facturable:', formatearDuracion(ticket.minutosFacturables)),
        _lineaCampo('Tarifa por hora:', 'S/. ' + formatearMoneda(ticket.tarifaHora)),
        _lineaSeparador('-'),
        _lineaCampo('TOTAL:', 'S/. ' + formatearMoneda(ticket.subtotal)),
        _lineaSeparador('='),
        'Gracias por su visita.',
        'Conserve este comprobante.',
        _lineaSeparador('=')
    ];

    return lineas.join('\n');
}

// ==================== BOLETA EN HTML ====================

/** Filas de la tabla de detalle de la boleta. */
function _filasDetalle(ticket) {
    const filas = [
        ['Boleta Nro', ticket.id || '-'],
        ['Emitida', formatearFecha(ticket.salida)],
        ['Parking', ticket.parkingNombre || ticket.parkingId || '-'],
        ['Direccion', ticket.parkingDireccion || '-'],
        ['Espacio', ticket.espacioAsignado || '-'],
        ['Usuario', ticket.username || '-'],
        ['Placa', ticket.placa || '-'],
        ['Vehiculo', ticket.tipoVehiculo || '-'],
        ['Entrada', formatearFecha(ticket.entrada)],
        ['Salida', formatearFecha(ticket.salida)],
        ['Tiempo de estancia', formatearDuracion(ticket.minutosEstancia)],
        ['Tiempo facturable', formatearDuracion(ticket.minutosFacturables)],
        ['Tarifa por hora', 'S/. ' + formatearMoneda(ticket.tarifaHora)]
    ];

    return filas.map(([etiqueta, valor]) =>
        `<tr><th>${escaparHtml(etiqueta)}</th><td>${escaparHtml(valor)}</td></tr>`
    ).join('');
}

/** HTML de la boleta (para vista previa dentro de la pagina). */
function construirHtmlBoleta(ticket) {
    if (!ticket) return '';
    return `
        <div class="ticket-preview">
            <div class="ticket-head">
                <h4>PARKUTP</h4>
                <p>BOLETA DE PAGO</p>
            </div>
            <table class="ticket-detalle">
                <tbody>${_filasDetalle(ticket)}</tbody>
            </table>
            <div class="ticket-total">
                <span>TOTAL</span>
                <strong>S/. ${escaparHtml(formatearMoneda(ticket.subtotal))}</strong>
            </div>
            <p class="ticket-nota">Gracias por su visita. Conserve este comprobante.</p>
        </div>
    `;
}

/** Documento HTML completo y autocontenido para la impresion. */
function construirDocumentoImpresion(ticket) {
    return `<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <title>Boleta ${escaparHtml(ticket.id || '')}</title>
    <style>
        * { box-sizing: border-box; }
        body {
            font-family: 'Segoe UI', Arial, sans-serif;
            color: #1e293b;
            margin: 0;
            padding: 24px;
            display: flex;
            justify-content: center;
        }
        .ticket-preview {
            width: 340px;
            border: 2px dashed #cbd5e1;
            border-radius: 12px;
            padding: 20px;
            background: #fff;
        }
        .ticket-head { text-align: center; margin-bottom: 16px; }
        .ticket-head h4 { margin: 0; font-size: 1.3rem; letter-spacing: 2px; }
        .ticket-head p { margin: 2px 0 0; font-size: .8rem; letter-spacing: 3px; color: #64748b; }
        table { width: 100%; border-collapse: collapse; }
        .ticket-detalle th, .ticket-detalle td {
            padding: 5px 0;
            font-size: .82rem;
            text-align: left;
            font-weight: 400;
        }
        .ticket-detalle th { color: #64748b; width: 45%; }
        .ticket-detalle td { font-weight: 600; }
        .ticket-total {
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-top: 2px solid #1e293b;
            margin-top: 14px;
            padding-top: 10px;
            font-size: .9rem;
        }
        .ticket-total strong { font-size: 1.15rem; }
        .ticket-nota { text-align: center; font-size: .72rem; color: #64748b; margin: 14px 0 0; }
        @media print {
            body { padding: 0; }
            .ticket-preview { border-color: #000; }
        }
    </style>
</head>
<body>
    ${construirHtmlBoleta(ticket)}
</body>
</html>`;
}

// ==================== DESCARGAR ====================

/**
 * Descarga la boleta como archivo de texto plano.
 * @param {object} ticket
 */
function descargarBoletaTexto(ticket) {
    if (!ticket) return;

    const texto = generarTextoBoleta(ticket);
    // BOM para que el Bloc de notas de Windows lea bien los acentos
    const blob = new Blob(['\ufeff' + texto], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);

    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = `boleta-${ticket.id || 'sin-id'}.txt`;
    document.body.appendChild(enlace);
    enlace.click();
    enlace.remove();

    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

// ==================== IMPRESION WEB ====================

/**
 * Imprime la boleta usando un iframe oculto: no abre ventanas emergentes
 * y el contenido sale tal cual se ve en la vista previa.
 * @param {object} ticket
 */
function imprimirBoleta(ticket) {
    if (!ticket) return;

    const documento = construirDocumentoImpresion(ticket);

    const iframe = document.createElement('iframe');
    iframe.setAttribute('aria-hidden', 'true');
    iframe.style.cssText =
        'position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden;';
    document.body.appendChild(iframe);

    const win = iframe.contentWindow;
    if (!win) {
        // Sin iframe disponible: se recurre a una ventana nueva
        iframe.remove();
        imprimirEnVentana(documento);
        return;
    }

    const doc = win.document;
    doc.open();
    doc.write(documento);
    doc.close();

    setTimeout(() => {
        win.focus();
        win.print();
        setTimeout(() => iframe.remove(), 1000);
    }, 150);
}

/** Alternativa de impresion en una pestana nueva. */
function imprimirEnVentana(documento) {
    const nueva = window.open('', '_blank');
    if (!nueva) {
        mostrarToast('El navegador bloqueo la ventana de impresion', 'error');
        return;
    }
    nueva.document.write(documento);
    nueva.document.close();
    nueva.focus();
    nueva.print();
}

// ==================== VISTA PREVIA (MODAL) ====================

/**
 * Muestra la boleta en el modal de la pagina.
 * @param {object} ticket
 */
function abrirModalBoleta(ticket) {
    if (!ticket) return;

    boletaActual = ticket;

    const contenedor = document.getElementById('boletaTicket');
    if (contenedor) {
        contenedor.innerHTML = construirHtmlBoleta(ticket);
    }

    const modal = document.getElementById('modalBoleta');
    if (modal) {
        modal.classList.remove('hidden');
    }
}

function cerrarModalBoleta() {
    const modal = document.getElementById('modalBoleta');
    if (modal) {
        modal.classList.add('hidden');
    }
    boletaActual = null;
}

/** Descarga la boleta que esta en pantalla. */
function descargarBoletaActual() {
    if (!boletaActual) return;
    descargarBoletaTexto(boletaActual);
}

/** Imprime la boleta que esta en pantalla. */
function imprimirBoletaActual() {
    if (!boletaActual) return;
    imprimirBoleta(boletaActual);
}
