/* Mapas (Leaflet + OpenStreetMap/CARTO). Principal: unidades e você. Mini: trajeto do ticket. */
const KM_LAT = 110.57;
const kmLng = (lat) => 111.32 * Math.cos((lat * Math.PI) / 180);

// Tiles do OpenStreetMap (sem chave de API). No tema escuro, o CSS inverte as cores dos tiles.
const TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const ATTR = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';

const MAPA = { mapa: null, tiles: null, user: null, pins: new Map(), ajustou: false, mini: null, miniTiles: null, miniMe: null, miniDest: null, miniLinha: null };

function novaCamada() {
  return L.tileLayer(TILE_URL, { attribution: ATTR, maxZoom: 19 });
}

function aplicarTemaMapa() {
  if (MAPA.mapa) { if (MAPA.tiles) MAPA.mapa.removeLayer(MAPA.tiles); MAPA.tiles = novaCamada().addTo(MAPA.mapa); }
  if (MAPA.mini) { if (MAPA.miniTiles) MAPA.mini.removeLayer(MAPA.miniTiles); MAPA.miniTiles = novaCamada().addTo(MAPA.mini); }
}

function iconeUsuario() {
  return L.divIcon({ className: '', html: '<div class="me"></div>', iconSize: [18, 18], iconAnchor: [9, 9] });
}

function iniciarMapa(aoMover, aoSelecionar) {
  MAPA.mapa = L.map('map', { zoomControl: true, scrollWheelZoom: true }).setView([S.loc.lat, S.loc.lng], 13);
  MAPA.tiles = novaCamada().addTo(MAPA.mapa);

  MAPA.user = L.marker([S.loc.lat, S.loc.lng], { icon: iconeUsuario(), draggable: true, zIndexOffset: 1000, title: 'Você está aqui. Arraste para simular outra posição.' }).addTo(MAPA.mapa);
  MAPA.user.bindTooltip('Você. Arraste para simular outra posição.', { direction: 'top', offset: [0, -10] });
  MAPA.user.on('dragend', () => {
    const p = MAPA.user.getLatLng();
    S.loc = { lat: p.lat, lng: p.lng };
    S.locFonte = 'manual';
    S.locRotulo = '';
    store.set('loc', S.loc); store.set('locFonte', 'manual'); store.set('locRotulo', '');
    $$('.zonas button').forEach((b) => b.classList.remove('active'));
    aoMover();
  });
  MAPA.aoSelecionar = aoSelecionar;

  // Agrupa unidades próximas; a cor da borda do grupo é a do pior nível de espera dentro dele.
  MAPA.cluster = L.markerClusterGroup({
    showCoverageOnHover: false, maxClusterRadius: 44, spiderfyOnMaxZoom: true, disableClusteringAtZoom: 17,
    iconCreateFunction: (c) => {
      const ordem = { ok: 0, warn: 1, bad: 2 };
      const pior = c.getAllChildMarkers().reduce((p, m) => (ordem[m.options.nivel] > ordem[p] ? m.options.nivel : p), 'ok');
      return L.divIcon({ className: '', html: `<div class="cl ${pior}">${c.getChildCount()}</div>`, iconSize: [40, 40], iconAnchor: [20, 20] });
    },
  }).addTo(MAPA.mapa);
}

function iconePin(e, selecionado) {
  const cls = `pin ${NIVEL[e.nivel]}${e.recomendado ? ' reco' : ''}${selecionado ? ' sel' : ''}`;
  return L.divIcon({ className: '', html: `<div class="${cls}">${e.esperaMin}</div>`, iconSize: [34, 34], iconAnchor: [17, 17] });
}

/** Sincroniza os pins com S.lista sem recriar o que não mudou. */
function atualizarMapa() {
  if (!MAPA.mapa) return;
  const vivos = new Set();
  const lista = visiveis();
  lista.forEach((e) => {
    vivos.add(e.id);
    const chave = `${e.nivel}|${e.esperaMin}|${e.recomendado}|${S.selecionado === e.id}`;
    const texto = `${e.nome} — espera de ${e.esperaMin} min`;
    let pin = MAPA.pins.get(e.id);
    if (!pin) {
      const m = L.marker([e.latitude, e.longitude], { icon: iconePin(e, S.selecionado === e.id), riseOnHover: true, nivel: NIVEL[e.nivel] });
      MAPA.cluster.addLayer(m);
      m.bindTooltip(texto, { direction: 'top', offset: [0, -14] });
      m.on('click', () => MAPA.aoSelecionar(e.id));
      pin = { m, chave };
      MAPA.pins.set(e.id, pin);
    } else if (pin.chave !== chave) {
      pin.m.options.nivel = NIVEL[e.nivel];
      pin.m.setIcon(iconePin(e, S.selecionado === e.id));
      pin.m.setTooltipContent(texto);
      pin.chave = chave;
    }
  });
  MAPA.pins.forEach((pin, id) => { if (!vivos.has(id)) { MAPA.cluster.removeLayer(pin.m); MAPA.pins.delete(id); } });

  const p = MAPA.user.getLatLng();
  if (Math.abs(p.lat - S.loc.lat) > 1e-7 || Math.abs(p.lng - S.loc.lng) > 1e-7) MAPA.user.setLatLng([S.loc.lat, S.loc.lng]);

  if (!MAPA.ajustou && S.lista.length) { ajustarMapa(false); MAPA.ajustou = true; }
}

/** Enquadra as unidades visíveis (resultado da busca) e a sua posição. */
function ajustarMapa(animar = true) {
  const lista = visiveis();
  if (!MAPA.mapa || !lista.length) return;
  MAPA.mapa.invalidateSize(false);
  if (lista.length === 1) {
    MAPA.mapa.setView([lista[0].latitude, lista[0].longitude], 15, { animate: animar });
    return;
  }
  const b = L.latLngBounds(lista.map((e) => [e.latitude, e.longitude])).extend([S.loc.lat, S.loc.lng]);
  MAPA.mapa.fitBounds(b, { padding: [48, 48], maxZoom: 14, animate: animar });
}

function focarUnidade(id) {
  const pin = MAPA.pins.get(id);
  if (!pin || !MAPA.mapa) return;
  MAPA.cluster.zoomToShowLayer(pin.m, () => MAPA.mapa.panTo(pin.m.getLatLng(), { animate: true })); // abre o grupo, se preciso
}

function recentralizarUsuario() {
  if (!MAPA.mapa) return;
  MAPA.user.setLatLng([S.loc.lat, S.loc.lng]);
  MAPA.mapa.panTo([S.loc.lat, S.loc.lng]);
}

// ---------------------------------------------------------------- mini mapa do trajeto
const SVG_PIN = '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0"/><circle cx="12" cy="10" r="3"/></svg>';

function iniciarMapaMini() {
  MAPA.mini = L.map('mapMini', { zoomControl: false, scrollWheelZoom: false, dragging: true, attributionControl: true }).setView([S.loc.lat, S.loc.lng], 13);
  MAPA.miniTiles = novaCamada().addTo(MAPA.mini);
  MAPA.miniMe = L.marker([S.loc.lat, S.loc.lng], { icon: iconeUsuario(), interactive: false }).addTo(MAPA.mini);
  MAPA.miniDest = L.marker([S.loc.lat, S.loc.lng], { icon: L.divIcon({ className: '', html: `<div class="dest">${SVG_PIN}</div>`, iconSize: [30, 30], iconAnchor: [15, 30] }), interactive: false }).addTo(MAPA.mini);
  MAPA.miniLinha = L.polyline([], { color: cssVar('--primary') || '#0f766e', weight: 4, opacity: .85, dashArray: '2 8', lineCap: 'round' }).addTo(MAPA.mini);
}

function atualizarMapaMini(destino) {
  if (!MAPA.mini || !destino) return;
  MAPA.miniMe.setLatLng([S.loc.lat, S.loc.lng]);
  MAPA.miniDest.setLatLng([destino.latitude, destino.longitude]);
  MAPA.miniLinha.setLatLngs([[S.loc.lat, S.loc.lng], [destino.latitude, destino.longitude]]);
  MAPA.miniLinha.setStyle({ color: cssVar('--primary') || '#0f766e' });
  MAPA.mini.fitBounds(L.latLngBounds([[S.loc.lat, S.loc.lng], [destino.latitude, destino.longitude]]), { padding: [44, 44], maxZoom: 16, animate: false });
}
