function inicializarMapaEspacios() {
    const selector = document.getElementById('selectorMapaEspacios');
    const contenedor = document.getElementById('mapaEspacios');
    if (!selector || !contenedor) return;

    let parkingsActuales = [];
    let reservasActuales = [];

    function dibujarMapa() {
        const parking = parkingsActuales.find(p => p.id === selector.value);
        if (!parking) {
            contenedor.innerHTML = '<p class="map-empty">No hay parkings disponibles para mostrar.</p>';
            return;
        }

        const ocupados = new Map(
            reservasActuales
                .filter(r => r.parkingId === parking.id && r.estado === 'ACTIVA')
                .map(r => [r.espacioAsignado, true])
        );
        const filas = Math.ceil(parking.espaciosTotales / 10);
        let html = `
            <div class="map-heading">
                <div>
                    <h3>${escaparHtml(parking.nombre)}</h3>
                    <p>${ocupados.size} ocupados de ${parking.espaciosTotales} espacios</p>
                </div>
                <div class="map-legend">
                    <span><i class="seat-swatch free"></i> Libre</span>
                    <span><i class="seat-swatch occupied"></i> Ocupado</span>
                </div>
            </div>
            <div class="parking-entrance">ENTRADA / CIRCULACION</div>
            <div class="seat-map" role="list" aria-label="Espacios de ${escaparHtml(parking.nombre)}">
        `;

        for (let fila = 0; fila < filas; fila++) {
            html += '<div class="seat-row">';
            for (let columna = 0; columna < 10; columna++) {
                const numero = fila * 10 + columna + 1;
                if (numero > parking.espaciosTotales) break;
                const etiqueta = String.fromCharCode(65 + fila) + String(columna + 1).padStart(2, '0');
                const estaOcupado = ocupados.has(etiqueta);
                html += `
                    <div class="parking-space ${estaOcupado ? 'occupied' : 'free'}"
                         role="listitem" aria-label="Espacio ${etiqueta}: ${estaOcupado ? 'ocupado' : 'libre'}">
                        <span>${etiqueta}</span>
                    </div>
                `;
                if (columna === 4 && numero < parking.espaciosTotales) {
                    html += '<div class="seat-aisle" aria-hidden="true"></div>';
                }
            }
            html += '</div>';
        }

        html += '</div>';
        contenedor.innerHTML = html;
    }

    async function actualizarMapaEspacios() {
        try {
            const [parkings, reservas] = await Promise.all([
                API.getParkings(),
                API.getReservas()
            ]);
            if (!Array.isArray(parkings) || !Array.isArray(reservas)) {
                throw new Error('La API devolvio datos invalidos para el mapa de espacios.');
            }

            const seleccionado = selector.value;
            parkingsActuales = parkings;
            reservasActuales = reservas;
            selector.innerHTML = parkings.map(p =>
                `<option value="${escaparHtml(p.id)}">${escaparHtml(p.nombre)}</option>`
            ).join('');
            if (parkings.some(p => p.id === seleccionado)) selector.value = seleccionado;
            dibujarMapa();
        } catch (error) {
            console.error('No se pudo actualizar el mapa de espacios:', error);
            contenedor.innerHTML = '<p class="map-empty error">No se pudo cargar el mapa. Reintenta en unos segundos.</p>';
        }
    }

    selector.addEventListener('change', dibujarMapa);
    window.actualizarMapaEspacios = actualizarMapaEspacios;
    actualizarMapaEspacios();
    window.setInterval(actualizarMapaEspacios, 30000);
}
