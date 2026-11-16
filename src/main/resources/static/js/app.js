/* Orquestracao: navegacao, polling e eventos. */
let simTimer = null;

// ------------------------------------------------------------------ navegacao
function irPara(view) {
  S.view = view;
  $$('.view').forEach((v) => v.classList.toggle('hidden', v.id !== `view-${view}`));
  $$('.nav button').forEach((b) => b.classList.toggle('active', b.dataset.go === view));
  if (view === 'ticket') { renderTicket(); $('#navDot').classList.add('hidden'); }
  if (view === 'painel') carregarPainel();
  if (view === 'metricas') carregarMetricas();
  if (view === 'descobrir') carregarLista();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

// ------------------------------------------------------------------ dados
async function carregarLista() {
  try {
    const q = new URLSearchParams({ lat: S.loc.lat, lng: S.loc.lng, modo: S.modo });
    if (S.categoria) q.set('categoria', S.categoria);
    S.lista = await api('/estabelecimentos?' + q);
    if (!S.arrastando) RADAR.escalaKm = escalaIdeal(S.lista);
    desenharRadar();
    renderLista();
    $('#locInfo').innerHTML = S.locFonte === 'gps'
      ? '📍 Usando a sua localização real. Arraste o radar para simular outra.'
      : 'Arraste o radar para <b style="color:var(--accent-text)">simular outra localização</b> (ponto inicial: Av. Paulista).';
  } catch (err) { console.error(err); }
}

function definirTicket(t, novo = false) {
  const anterior = S.ticket;
  S.ticket = t;
  S.ticketId = t.id;
  store.set('ticketId', t.id);
  if (novo) { S.espera0 = t.esperaMin || 0; }
  else if (t.status === 'AGUARDANDO' && (t.esperaMin || 0) > S.espera0) S.espera0 = t.esperaMin;
  store.set('espera0', S.espera0);
  const est = S.lista.find((x) => x.id === t.estabelecimentoId);
  if (est) S.estabTicket = est;

  // alertas quando o aviso muda
  if (anterior && anterior.id === t.id && anterior.aviso !== t.aviso) {
    if (t.aviso === 'PREPARE_SE') toast(`Prepare-se: saia em ~${t.saiEmMin} min.`, 'alert', '⏰');
    if (t.aviso === 'SAIA_AGORA') { toast('Hora de sair! Você chega na hora da sua vez.', 'alert', '🚀'); beep(); }
    if (t.aviso === 'SUA_VEZ') { toast(`É a sua vez! Guichê ${t.guiche}.`, 'alert', '📣'); beep([880, 1175, 1568]); }
  }
  const urgente = t.aviso === 'SAIA_AGORA' || t.aviso === 'SUA_VEZ';
  $('#navDot').classList.toggle('hidden', !(urgente && S.view !== 'ticket'));
}

async function carregarTicket() {
  if (!S.ticketId) return;
  try {
    const t = await api('/tickets/' + S.ticketId);
    definirTicket(t);
    if (S.view === 'ticket') renderTicket();
  } catch (err) {
    if (/não encontrado/i.test(err.message)) { S.ticket = null; S.ticketId = null; store.del('ticketId'); if (S.view === 'ticket') renderTicket(); }
  }
}

async function carregarPainel() {
  try {
    if (!$('#painelSel').options.length) {
      const todos = await api('/estabelecimentos');
      $('#painelSel').innerHTML = todos.slice().sort((a, b) => a.nome.localeCompare(b.nome)).map((e) => `<option value="${e.id}">${CAT[e.categoria] || ''} ${esc(e.nome)}</option>`).join('');
      if (!S.painelId || !todos.some((e) => e.id === +S.painelId)) S.painelId = todos[0].id;
      $('#painelSel').value = S.painelId;
    }
    if (S.view !== 'painel') return;
    renderPainel(await api(`/estabelecimentos/${S.painelId}/painel`));
  } catch (err) { console.error(err); }
}

async function carregarMetricas() {
  try {
    const m = await api('/metricas' + (S.metricasEstab ? `?estabelecimentoId=${S.metricasEstab}` : ''));
    S.fator = m.fatorRelogio;
    atualizarPillDemo(m.simulacao);
    if (S.view === 'metricas') renderMetricas(m);
  } catch (err) { console.error(err); }
}

function atualizarPillDemo(ligado) {
  $('#demoPill').classList.toggle('off', !ligado);
  $('#demoText').textContent = ligado ? `Demo ao vivo${S.fator > 1 ? ' ×' + S.fator : ''}` : 'Demo pausada';
}

// ------------------------------------------------------------------ eventos
document.addEventListener('click', async (ev) => {
  const go = ev.target.closest('[data-go]');
  if (go) { ev.preventDefault(); irPara(go.dataset.go); return; }

  const join = ev.target.closest('[data-join]');
  if (join) { ev.stopPropagation(); abrirModalEntrar(+join.dataset.join); return; }

  const card = ev.target.closest('.ecard');
  if (card && !ev.target.closest('button')) {
    S.selecionado = S.selecionado === +card.dataset.id ? null : +card.dataset.id;
    desenharRadar(); renderLista(); return;
  }

  const chip = ev.target.closest('[data-cat]');
  if (chip) { S.categoria = chip.dataset.cat || null; renderChips(); carregarLista(); return; }

  const modo = ev.target.closest('#modoSeg [data-modo]');
  if (modo) {
    S.modo = modo.dataset.modo; store.set('modo', S.modo);
    $$('#modoSeg button').forEach((b) => b.classList.toggle('active', b === modo));
    carregarLista(); return;
  }

  const modoTk = ev.target.closest('#modoTicket [data-modo]');
  if (modoTk && S.ticket) {
    S.modo = modoTk.dataset.modo; store.set('modo', S.modo);
    $$('#modoSeg button').forEach((b) => b.classList.toggle('active', b.dataset.modo === S.modo));
    try { definirTicket(await api(`/tickets/${S.ticket.id}/localizacao`, { method: 'PUT', body: { latitude: S.loc.lat, longitude: S.loc.lng, modo: S.modo } })); renderTicket(); }
    catch (err) { toast(err.message, 'err', '⚠️'); }
    return;
  }

  const act = ev.target.closest('[data-act]');
  if (act) await executarAcao(act);
});

async function executarAcao(btn) {
  const acao = btn.dataset.act;
  btn.disabled = true;
  try {
    if (acao === 'cancelar') {
      if (!confirm('Sair da fila? Você perde o seu lugar.')) { btn.disabled = false; return; }
      pararSimulacaoTrajeto();
      definirTicket(await api(`/tickets/${S.ticketId}/cancelar`, { method: 'POST' }));
      renderTicket();
    } else if (acao === 'simular') {
      alternarSimulacaoTrajeto();
    } else if (acao === 'chamar') {
      const t = await api(`/estabelecimentos/${S.painelId}/chamar-proximo`, { method: 'POST' });
      toast(`${t.codigo} (${t.nomeCliente}) → guichê ${t.guiche}`, 'ok', '📣');
      carregarPainel();
    } else if (acao === 'finalizar' || acao === 'ausente') {
      await api(`/tickets/${btn.dataset.id}/${acao}`, { method: 'POST' });
      carregarPainel();
    }
  } catch (err) { toast(err.message, 'err', '⚠️'); }
  finally { btn.disabled = false; }
}

// "Simular trajeto": o usuario se aproxima do destino e a hora de sair se recalcula
function pararSimulacaoTrajeto() {
  if (simTimer) { clearInterval(simTimer); simTimer = null; }
  const b = $('#btnSim'); if (b) b.textContent = '▶ Simular trajeto';
}
function alternarSimulacaoTrajeto() {
  if (simTimer) { pararSimulacaoTrajeto(); return; }
  const e = S.lista.find((x) => x.id === S.ticket.estabelecimentoId) || S.estabTicket;
  if (!e) return;
  $('#btnSim').textContent = '⏸ Parar simulação';
  simTimer = setInterval(async () => {
    const dKm = Math.hypot((e.longitude - S.loc.lng) * kmLng(S.loc.lat), (e.latitude - S.loc.lat) * KM_LAT);
    if (dKm < 0.12 || !S.ticket || S.ticket.status !== 'AGUARDANDO') { pararSimulacaoTrajeto(); return; }
    S.loc = { lat: S.loc.lat + (e.latitude - S.loc.lat) * 0.14, lng: S.loc.lng + (e.longitude - S.loc.lng) * 0.14 };
    S.locFonte = 'manual'; store.set('loc', S.loc);
    try { definirTicket(await api(`/tickets/${S.ticket.id}/localizacao`, { method: 'PUT', body: { latitude: S.loc.lat, longitude: S.loc.lng, modo: S.modo } })); renderTicket(); if (simTimer) $('#btnSim').textContent = '⏸ Parar simulação'; }
    catch (_) { pararSimulacaoTrajeto(); }
  }, 1500);
}

$('#geoBtn').onclick = () => {
  if (!navigator.geolocation) { toast('Seu navegador não permite geolocalização.', 'err', '⚠️'); return; }
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      S.loc = { lat: pos.coords.latitude, lng: pos.coords.longitude };
      S.locFonte = 'gps'; store.set('loc', S.loc); store.set('locFonte', 'gps');
      toast('Localização atualizada.', 'ok', '📍');
      carregarLista();
    },
    () => toast('Não consegui acessar sua localização. Arraste o radar para simular.', 'err', '📍'),
    { enableHighAccuracy: true, timeout: 8000 });
};

$('#painelSel').onchange = (ev) => { S.painelId = +ev.target.value; store.set('painelId', S.painelId); carregarPainel(); };
document.addEventListener('change', (ev) => { if (ev.target.id === 'metEstab') { S.metricasEstab = ev.target.value; carregarMetricas(); } });

$('#demoPill').onclick = async () => {
  const ligado = !$('#demoPill').classList.contains('off');
  try { await api('/simulacao', { method: 'POST', body: { habilitada: !ligado } }); atualizarPillDemo(!ligado); toast(!ligado ? 'Piloto automático ligado.' : 'Piloto automático pausado: agora só o painel move as filas.', '', '🤖'); }
  catch (err) { toast(err.message, 'err', '⚠️'); }
};

function aplicarTema(t) {
  document.documentElement.dataset.theme = t;
  $('#themeBtn').textContent = t === 'dark' ? '🌙' : '☀️';
  store.set('tema', t);
}
$('#themeBtn').onclick = () => aplicarTema(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark');

// ------------------------------------------------------------------ inicio
function iniciar() {
  aplicarTema(store.get('tema', 'dark'));
  $$('#modoSeg button').forEach((b) => b.classList.toggle('active', b.dataset.modo === S.modo));
  renderChips();
  iniciarRadar(
    (final) => carregarLista(),
    (id) => { S.selecionado = S.selecionado === id ? null : id; desenharRadar(); renderLista(); const c = $(`.ecard[data-id="${id}"]`); if (c) c.scrollIntoView({ behavior: 'smooth', block: 'center' }); });
  iniciarBot();
  carregarMetricas(); // descobre fator do relogio e estado da simulacao
  carregarLista().then(carregarTicket);

  let tick = 0;
  setInterval(() => {
    tick++;
    if (document.hidden) return;
    if (S.ticketId && (S.view === 'ticket' || tick % 2 === 0)) carregarTicket();
    if (S.view === 'descobrir' && !S.arrastando) carregarLista();
    if (S.view === 'painel') carregarPainel();
    if (S.view === 'metricas' && tick % 3 === 0) carregarMetricas();
  }, 2000);
}
iniciar();
