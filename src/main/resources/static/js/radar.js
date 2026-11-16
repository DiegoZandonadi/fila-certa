/* Radar de filas: o usuario esta no centro; cada local e um "blip" colorido pelo tempo de espera. */
const RADAR = { cx: 300, cy: 300, R: 250, escalaKm: 6 };
const KM_LAT = 110.57;
const kmLng = (lat) => 111.32 * Math.cos((lat * Math.PI) / 180);

// Escala "raiz quadrada": locais proximos ganham espaco, os distantes cabem na borda.
// Com escala 9 km os aneis ficam em 1, 4 e 9 km.
function escalaIdeal(lista) {
  const max = Math.max(1, ...lista.map((e) => e.distanciaKm || 0)) * 1.05;
  return [4, 9, 16, 25, 36, 64].find((s) => s >= max) || 100;
}

function projetar(lat, lng) {
  const dx = (lng - S.loc.lng) * kmLng(S.loc.lat);
  const dy = (lat - S.loc.lat) * KM_LAT;
  const d = Math.hypot(dx, dy);
  if (d < 0.001) return [RADAR.cx, RADAR.cy];
  const r = Math.min(RADAR.R + 14, RADAR.R * Math.sqrt(d / RADAR.escalaKm));
  return [RADAR.cx + (dx / d) * r, RADAR.cy - (dy / d) * r];
}

function desenharRadar() {
  const svg = $('#radarSvg');
  if (!svg) return;
  const { cx, cy, R, escalaKm } = RADAR;
  const t = Date.now() / 1000;
  const fase = (d) => `animation-delay:-${(t % d).toFixed(2)}s`; // mantem a pulsacao continua entre redesenhos
  let h = '';

  // aneis de distancia
  [1 / 3, 2 / 3, 1].forEach((f) => {
    h += `<circle class="ring" cx="${cx}" cy="${cy}" r="${R * f}"/>`;
    h += `<text class="ring-label" x="${cx + 6}" y="${cy - R * f + 14}">${fmtDec(+(escalaKm * f * f).toFixed(1))} km</text>`;
  });
  h += `<line class="axis" x1="${cx - R - 14}" y1="${cy}" x2="${cx + R + 14}" y2="${cy}"/><line class="axis" x1="${cx}" y1="${cy - R - 14}" x2="${cx}" y2="${cy + R + 14}"/>`;
  h += `<text class="ring-label" x="${cx - 4}" y="${cy - R - 20}" text-anchor="middle">N</text>`;

  // blips
  S.lista.forEach((e, i) => {
    const [x, y] = projetar(e.latitude, e.longitude);
    const cor = NIVEL_COR[e.nivel];
    const sel = S.selecionado === e.id ? ' sel' : '';
    const reco = e.recomendado ? ' reco' : '';
    const anchor = x > 440 ? 'end' : x < 160 ? 'start' : 'middle';
    const lx = anchor === 'end' ? x + 14 : anchor === 'start' ? x - 14 : x;
    const nome = e.nome.length > 20 ? e.nome.slice(0, 19) + '…' : e.nome;
    h += `<g class="blip${sel}${reco}" data-id="${e.id}" style="--i:${i}">
      <circle class="halo" cx="${x}" cy="${y}" r="20" fill="${cor}" style="${fase(e.recomendado ? 1.2 : 2.6)}"/>
      <circle class="core" cx="${x}" cy="${y}" r="15" fill="var(--bg)" stroke="${cor}"/>
      <text x="${x}" y="${y + 5}" text-anchor="middle" font-size="14">${CAT[e.categoria] || '📍'}</text>
      <text class="lab" x="${lx}" y="${y + 31}" text-anchor="${anchor}">${esc(nome)}</text>
      <text class="sub" x="${lx}" y="${y + 43}" text-anchor="${anchor}">${e.esperaMin} min · ${e.pessoasNaFila} na fila</text>
      ${e.recomendado ? `<text x="${x + 17}" y="${y - 12}" font-size="14">⭐</text>` : ''}
    </g>`;
  });

  // voce
  h += `<g class="you"><circle class="pulse" cx="${cx}" cy="${cy}" r="14" style="${fase(2)}"/><circle class="dot" cx="${cx}" cy="${cy}" r="8"/><text x="${cx}" y="${cy + 28}" text-anchor="middle">VOCÊ</text></g>`;
  svg.innerHTML = h;
}

/** Interacoes: clicar num blip seleciona; arrastar o radar "move" o usuario no mapa. */
function iniciarRadar(aoMudarLocal, aoSelecionar) {
  const radar = $('#radar');
  let drag = null, throttle = 0;

  radar.addEventListener('pointerdown', (ev) => {
    const blip = ev.target.closest('.blip');
    if (blip) { aoSelecionar(+blip.dataset.id); return; }
    drag = { x: ev.clientX, y: ev.clientY, lat: S.loc.lat, lng: S.loc.lng, escala: RADAR.escalaKm, moveu: false };
    S.arrastando = true;
    radar.setPointerCapture(ev.pointerId);
  });

  radar.addEventListener('pointermove', (ev) => {
    if (!drag) return;
    const rect = radar.getBoundingClientRect();
    const k = 600 / rect.width; // px da tela -> unidades do viewBox
    // gesto aproximado: ~35% da escala por raio do radar (a escala visual e nao linear)
    const dxKm = ((ev.clientX - drag.x) * k) / RADAR.R * drag.escala * 0.35;
    const dyKm = ((ev.clientY - drag.y) * k) / RADAR.R * drag.escala * 0.35;
    if (Math.hypot(ev.clientX - drag.x, ev.clientY - drag.y) > 3) drag.moveu = true;
    if (!drag.moveu) return;
    RADAR.escalaKm = drag.escala; // escala congelada durante o arraste
    // o mapa anda junto com o dedo => o usuario se move no sentido oposto
    S.loc = { lat: drag.lat + dyKm / KM_LAT, lng: drag.lng - dxKm / kmLng(drag.lat) };
    S.locFonte = 'manual';
    desenharRadar();
    const agora = Date.now();
    if (agora - throttle > 350) { throttle = agora; aoMudarLocal(false); }
  });

  const fim = () => {
    if (!drag) return;
    const moveu = drag.moveu;
    drag = null; S.arrastando = false;
    if (moveu) { store.set('loc', S.loc); store.set('locFonte', S.locFonte); aoMudarLocal(true); }
  };
  radar.addEventListener('pointerup', fim);
  radar.addEventListener('pointercancel', fim);
}

/** Mini mapa do trajeto: voce -> destino (aba Meu ticket). */
function desenharRota(svg, origem, destino, nomeDestino, emoji) {
  const W = 420, H = 240, pad = 46;
  const dx = (destino.lng - origem.lng) * kmLng(origem.lat);
  const dy = (destino.lat - origem.lat) * KM_LAT;
  const alcance = Math.max(0.3, Math.hypot(dx, dy));
  const esc2 = Math.min((W - pad * 2) / (Math.abs(dx) * 2 || 1), (H - pad * 2) / (Math.abs(dy) * 2 || 1), (W - pad * 2) / (alcance * 2 || 1));
  const mx = (origem.lng + destino.lng) / 2, my = (origem.lat + destino.lat) / 2;
  const pos = (lat, lng) => [W / 2 + (lng - mx) * kmLng(my) * esc2, H / 2 - (lat - my) * KM_LAT * esc2];
  const [x1, y1] = pos(origem.lat, origem.lng);
  const [x2, y2] = pos(destino.lat, destino.lng);
  let h = '';
  for (let i = 1; i < 6; i++) h += `<line class="axis" x1="${i * W / 6}" y1="0" x2="${i * W / 6}" y2="${H}"/>`;
  for (let i = 1; i < 4; i++) h += `<line class="axis" x1="0" y1="${i * H / 4}" x2="${W}" y2="${i * H / 4}"/>`;
  h += `<line class="route" x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}"/>`;
  h += `<g class="you"><circle class="pulse" cx="${x1}" cy="${y1}" r="12"/><circle class="dot" cx="${x1}" cy="${y1}" r="7"/><text x="${x1}" y="${y1 + 24}" text-anchor="middle">VOCÊ</text></g>`;
  h += `<g class="blip"><circle class="core" cx="${x2}" cy="${y2}" r="15" fill="var(--bg)" stroke="var(--accent-2)"/><text x="${x2}" y="${y2 + 5}" text-anchor="middle" font-size="14">${emoji}</text><text class="lab" x="${x2}" y="${y2 + 31}" text-anchor="middle">${esc(nomeDestino.length > 24 ? nomeDestino.slice(0, 23) + '…' : nomeDestino)}</text></g>`;
  svg.setAttribute('viewBox', `0 0 ${W} ${H}`);
  svg.innerHTML = h;
}
