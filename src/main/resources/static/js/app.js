/* Orquestração: navegação, polling e eventos. */
let simTimer = null;

// ------------------------------------------------------------------ navegação
function irPara(view) {
  S.view = view;
  $$('.view').forEach((v) => v.classList.toggle('hidden', v.id !== `view-${view}`));
  $$('.menu button').forEach((b) => b.classList.toggle('active', b.dataset.go === view));
  if (view === 'ticket') { renderTicket(); $('#navDot').classList.add('hidden'); }
  if (view === 'painel') carregarPainel();
  if (view === 'metricas') carregarMetricas();
  if (view === 'ia') carregarIA();
  if (view === 'descobrir') { carregarLista(); setTimeout(() => MAPA.mapa && MAPA.mapa.invalidateSize(), 50); }
  window.scrollTo({ top: 0 });
}

// ------------------------------------------------------------------ dados
async function carregarLista() {
  try {
    const q = new URLSearchParams({ lat: S.loc.lat, lng: S.loc.lng, modo: S.modo });
    if (S.categoria) q.set('categoria', S.categoria);
    S.lista = await api('/estabelecimentos?' + q);
    if (!S.categoria) atualizarCategorias();
    if (centralizarSeNecessario()) return carregarLista();
    atualizarMapa();
    renderLista();
    $('#locInfo').textContent = S.locFonte === 'gps'
      ? 'Usando a sua localização real. Arraste o ponto azul para ajustar.'
      : S.locRotulo
        ? `Partindo de: ${S.locRotulo}. Arraste o ponto azul para ajustar.`
        : 'Arraste o ponto azul ou informe seu endereço (posição inicial: centro da cidade).';
  } catch (err) { console.error(err); }
}

function definirTicket(t, novo = false) {
  const anterior = S.ticket;
  S.ticket = t;
  S.ticketId = t.id;
  store.set('ticketId', t.id);
  if (novo) S.espera0 = t.esperaMin || 0;
  else if (t.status === 'AGUARDANDO' && (t.esperaMin || 0) > S.espera0) S.espera0 = t.esperaMin;
  store.set('espera0', S.espera0);
  const est = S.lista.find((x) => x.id === t.estabelecimentoId);
  if (est) S.estabTicket = est;

  if (anterior && anterior.id === t.id && anterior.aviso !== t.aviso) {
    if (t.aviso === 'PREPARE_SE') toast(`Prepare-se: saia em cerca de ${t.saiEmMin} min.`, 'alert');
    if (t.aviso === 'SAIA_AGORA') { toast('Hora de sair. Você chega na hora da sua vez.', 'alert'); beep(); }
    if (t.aviso === 'SUA_VEZ') { toast(`É a sua vez. Guichê ${t.guiche}.`, 'alert'); beep([880, 1175, 1568]); }
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
    if (/não encontrado/i.test(err.message)) {
      S.ticket = null; S.ticketId = null; store.del('ticketId');
      if (S.view === 'ticket') renderTicket();
    }
  }
}

async function carregarPainel() {
  try {
    if (!$('#painelSel').options.length) {
      const todos = await api('/estabelecimentos');
      $('#painelSel').innerHTML = todos.slice().sort((a, b) => a.nome.localeCompare(b.nome)).map((e) => `<option value="${e.id}">${esc(e.nome)}</option>`).join('');
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
    atualizarSimulacao(m.simulacao);
    if (S.view === 'metricas') renderMetricas(m);
  } catch (err) { console.error(err); }
}

async function carregarIA() {
  try {
    const v = await api('/ml/previsoes');
    if (S.view === 'ia') renderIA(v);
  } catch (err) { console.error(err); }
}

function atualizarSimulacao(ligada) {
  const sw = $('#demoPill');
  sw.setAttribute('aria-checked', String(ligada));
  $('#demoText').textContent = ligada ? 'Simulação ativa' : 'Simulação pausada';
  $('#demoSub').textContent = ligada ? `Fila de demonstração${S.fator > 1 ? ' · ×' + S.fator : ''}` : 'Só o painel move as filas';
}

// ------------------------------------------------------------------ eventos
document.addEventListener('click', async (ev) => {
  const go = ev.target.closest('[data-go]');
  if (go) { ev.preventDefault(); irPara(go.dataset.go); return; }

  const join = ev.target.closest('[data-join]');
  if (join) { ev.stopPropagation(); abrirModalEntrar(+join.dataset.join); return; }

  const card = ev.target.closest('.eitem');
  if (card && !ev.target.closest('button')) { selecionarUnidade(+card.dataset.id); return; }

  const cat = ev.target.closest('[data-cat]');
  if (cat) { S.categoria = cat.dataset.cat || null; renderChips(); carregarLista(); return; }

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
    catch (err) { toast(err.message, 'err'); }
    return;
  }

  const act = ev.target.closest('[data-act]');
  if (act && act.dataset.act === 'limpar-busca') { definirBusca(''); $('#busca').focus(); return; }
  if (act) await executarAcao(act);
});

// ---------------------------------------------------------------- busca de unidade
function definirBusca(texto) {
  S.busca = texto;
  $('#busca').value = texto;
  $('#buscaLimpar').classList.toggle('hidden', !texto);
  const lista = visiveis();
  S.selecionado = texto.trim() && lista.length === 1 ? lista[0].id : (lista.some((e) => e.id === S.selecionado) ? S.selecionado : null);
  atualizarMapa(); renderLista();
  ajustarMapa(true);
}
let buscaTimer = null;
$('#busca').addEventListener('input', (ev) => {
  clearTimeout(buscaTimer);
  const v = ev.target.value;
  $('#buscaLimpar').classList.toggle('hidden', !v);
  buscaTimer = setTimeout(() => definirBusca(v), 180);
});
$('#busca').addEventListener('keydown', (ev) => {
  if (ev.key === 'Escape') definirBusca('');
  if (ev.key === 'Enter') {
    const primeiro = visiveis()[0];
    if (primeiro) { S.selecionado = primeiro.id; atualizarMapa(); renderLista(); focarUnidade(primeiro.id); }
  }
});
$('#buscaLimpar').onclick = () => { definirBusca(''); $('#busca').focus(); };
document.addEventListener('keydown', (ev) => {
  if (ev.key === '/' && S.view === 'descobrir' && !/INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName)) { ev.preventDefault(); $('#busca').focus(); }
});

function selecionarUnidade(id) {
  S.selecionado = S.selecionado === id ? null : id;
  atualizarMapa(); renderLista();
  if (S.selecionado) {
    focarUnidade(id);
    const c = $(`.eitem[data-id="${id}"]`);
    if (c) c.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }
}

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
      toast(`${t.codigo} (${t.nomeCliente}) chamado ao guichê ${t.guiche}.`, 'ok');
      carregarPainel();
    } else if (acao === 'finalizar' || acao === 'ausente') {
      await api(`/tickets/${btn.dataset.id}/${acao}`, { method: 'POST' });
      carregarPainel();
    }
  } catch (err) { toast(err.message, 'err'); }
  finally { btn.disabled = false; }
}

// Simular trajeto: o usuário se aproxima do destino e a hora de sair se recalcula
function rotuloSimulacao(rodando) {
  const b = $('#btnSim');
  if (b) { b.innerHTML = `${ic(rodando ? 'pause' : 'play')}${rodando ? 'Parar simulação' : 'Simular trajeto'}`; icones(); }
}
function pararSimulacaoTrajeto() {
  if (simTimer) { clearInterval(simTimer); simTimer = null; }
  rotuloSimulacao(false);
}
function alternarSimulacaoTrajeto() {
  if (simTimer) { pararSimulacaoTrajeto(); return; }
  const e = S.lista.find((x) => x.id === S.ticket.estabelecimentoId) || S.estabTicket;
  if (!e) return;
  rotuloSimulacao(true);
  simTimer = setInterval(async () => {
    const dKm = Math.hypot((e.longitude - S.loc.lng) * kmLng(S.loc.lat), (e.latitude - S.loc.lat) * KM_LAT);
    if (dKm < 0.12 || !S.ticket || S.ticket.status !== 'AGUARDANDO') { pararSimulacaoTrajeto(); return; }
    S.loc = { lat: S.loc.lat + (e.latitude - S.loc.lat) * 0.14, lng: S.loc.lng + (e.longitude - S.loc.lng) * 0.14 };
    S.locFonte = 'manual'; store.set('loc', S.loc);
    try {
      definirTicket(await api(`/tickets/${S.ticket.id}/localizacao`, { method: 'PUT', body: { latitude: S.loc.lat, longitude: S.loc.lng, modo: S.modo } }));
      renderTicket(); rotuloSimulacao(!!simTimer);
    } catch (_) { pararSimulacaoTrajeto(); }
  }, 1500);
}

/** Define de onde a pessoa parte (GPS, endereço, atalho de região ou arraste no mapa). */
function definirLocalizacao(lat, lng, fonte, rotulo = '') {
  S.loc = { lat, lng };
  S.locFonte = fonte;
  S.locRotulo = rotulo;
  store.set('loc', S.loc); store.set('locFonte', fonte); store.set('locRotulo', rotulo);
  $$('.zonas button').forEach((b) => b.classList.toggle('active', fonte === 'zona' && b.dataset.zona === S.zonaAtiva));
  recentralizarUsuario();
  MAPA.ajustou = false;
  carregarLista().then(() => ajustarMapa(true));
}

$('#geoBtn').onclick = () => {
  if (!navigator.geolocation) { toast('Este navegador não oferece geolocalização. Informe seu endereço no campo de localização.', 'err'); return; }
  if (!window.isSecureContext) {
    toast('O navegador só libera o GPS em páginas seguras (HTTPS ou localhost). Informe seu endereço no campo de localização.', 'err');
    return;
  }
  navigator.geolocation.getCurrentPosition(
    (pos) => { S.zonaAtiva = null; definirLocalizacao(pos.coords.latitude, pos.coords.longitude, 'gps'); toast('Localização atualizada.', 'ok'); },
    (e) => {
      const motivo = { 1: 'A permissão de localização foi negada. Libere o acesso no ícone de cadeado da barra de endereço.', 2: 'Não foi possível determinar a posição agora.', 3: 'A localização demorou demais para responder.' }[e.code] || 'Falha ao obter a localização.';
      toast(`${motivo} Você também pode informar o endereço no campo ao lado.`, 'err');
    },
    { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 });
};

// ---------------------------------------------------------------- endereço (OpenStreetMap / Nominatim)
/** Pontos de referência calculados a partir das unidades carregadas (funciona para qualquer cidade). */
function zonasDaCidade() {
  if (!S.lista.length) return null;
  const lats = S.lista.map((e) => e.latitude), lngs = S.lista.map((e) => e.longitude);
  const minLat = Math.min(...lats), maxLat = Math.max(...lats), minLng = Math.min(...lngs), maxLng = Math.max(...lngs);
  const cLat = (minLat + maxLat) / 2, cLng = (minLng + maxLng) / 2, dLat = maxLat - minLat, dLng = maxLng - minLng;
  return {
    centro: { lat: cLat, lng: cLng, rotulo: 'Região central' },
    oeste: { lat: cLat, lng: minLng + dLng * 0.18, rotulo: 'Região oeste' },
    leste: { lat: cLat, lng: maxLng - dLng * 0.18, rotulo: 'Região leste' },
    norte: { lat: maxLat - dLat * 0.18, lng: cLng, rotulo: 'Região norte' },
    sul: { lat: minLat + dLat * 0.18, lng: cLng, rotulo: 'Região sul' },
  };
}

function atualizarCategorias() {
  const cats = [...new Set(S.lista.map((e) => e.categoria))];
  if (JSON.stringify(cats) !== JSON.stringify(S.catsDisponiveis)) { S.catsDisponiveis = cats; renderChips(); }
}

/** Primeira carga (ou posição salva longe de qualquer unidade): centraliza na cidade das unidades. */
function centralizarSeNecessario() {
  if (S.centralizou || !S.lista.length) return false;
  S.centralizou = true;
  const z = zonasDaCidade();
  const menorKm = Math.min(...S.lista.map((e) => Math.hypot((e.longitude - S.loc.lng) * kmLng(S.loc.lat), (e.latitude - S.loc.lat) * KM_LAT)));
  if (S.locFonte === 'padrao' || menorKm > 20) {
    S.loc = { lat: z.centro.lat, lng: z.centro.lng };
    S.locFonte = 'padrao'; S.locRotulo = '';
    store.del('loc'); store.set('locFonte', 'padrao'); store.del('locRotulo');
    recentralizarUsuario();
    MAPA.ajustou = false;
    return true; // recarrega com as distancias a partir do novo ponto
  }
  return false;
}

async function buscarEndereco() {
  const q = $('#local').value.trim();
  const box = $('#sugestoes');
  if (q.length < 3) { box.classList.add('hidden'); return; }
  box.classList.remove('hidden');
  box.innerHTML = '<div class="info">Buscando…</div>';
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), 9000);
  try {
    const url = 'https://nominatim.openstreetmap.org/search?' + new URLSearchParams({ format: 'jsonv2', q, countrycodes: 'br', limit: '5', 'accept-language': 'pt-BR' });
    const res = await fetch(url, { signal: ctl.signal });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const itens = await res.json();
    if (!itens.length) { box.innerHTML = '<div class="info">Nenhum endereço encontrado. Tente incluir o bairro ou a cidade.</div>'; return; }
    box.innerHTML = itens.map((r, i) => `<button type="button" role="option" data-i="${i}">${esc(r.display_name)}</button>`).join('');
    box.dataset.itens = JSON.stringify(itens.map((r) => ({ lat: +r.lat, lng: +r.lon, nome: r.display_name })));
  } catch (err) {
    box.innerHTML = '<div class="info">Não foi possível buscar o endereço agora. Use os atalhos de região ou arraste o ponto azul no mapa.</div>';
  } finally { clearTimeout(timer); }
}

$('#localBuscar').onclick = buscarEndereco;
$('#local').addEventListener('keydown', (ev) => {
  if (ev.key === 'Enter') { ev.preventDefault(); buscarEndereco(); }
  if (ev.key === 'Escape') $('#sugestoes').classList.add('hidden');
});
$('#sugestoes').addEventListener('click', (ev) => {
  const b = ev.target.closest('[data-i]');
  if (!b) return;
  const item = JSON.parse($('#sugestoes').dataset.itens)[+b.dataset.i];
  const curto = item.nome.split(',').slice(0, 3).join(',').trim();
  $('#sugestoes').classList.add('hidden');
  $('#local').value = curto;
  S.zonaAtiva = null;
  definirLocalizacao(item.lat, item.lng, 'endereco', curto);
});
document.addEventListener('click', (ev) => { if (!ev.target.closest('.origem')) $('#sugestoes').classList.add('hidden'); });
$$('.zonas button').forEach((b) => b.addEventListener('click', () => {
  const z = (zonasDaCidade() || {})[b.dataset.zona];
  if (!z) return;
  S.zonaAtiva = b.dataset.zona;
  $('#local').value = '';
  definirLocalizacao(z.lat, z.lng, 'zona', z.rotulo);
}));

$('#painelSel').onchange = (ev) => { S.painelId = +ev.target.value; store.set('painelId', S.painelId); S.assinaturas.painel = null; carregarPainel(); };
document.addEventListener('change', (ev) => { if (ev.target.id === 'metEstab') { S.metricasEstab = ev.target.value; carregarMetricas(); } });

$('#iaColetar').onclick = async (ev) => {
  const b = ev.currentTarget; b.disabled = true;
  try {
    const r = await api('/ml/coletar', { method: 'POST', timeout: 40000 });
    toast(r.novas ? `${r.novas} leituras novas de Barueri.` : 'Sem leituras novas: a Prefeitura ainda não atualizou o painel.', 'ok');
    S.assinaturas.ia = null; carregarIA();
  } catch (err) { toast(err.message, 'err'); }
  finally { b.disabled = false; }
};

$('#demoPill').onclick = async () => {
  const ligada = $('#demoPill').getAttribute('aria-checked') === 'true';
  try {
    await api('/simulacao', { method: 'POST', body: { habilitada: !ligada } });
    atualizarSimulacao(!ligada);
    toast(!ligada ? 'Simulação ligada.' : 'Simulação pausada: agora só o painel move as filas.', '');
  } catch (err) { toast(err.message, 'err'); }
};

function aplicarTema(t) {
  S.tema = t;
  document.documentElement.dataset.theme = t;
  const claro = t === 'light';
  $('#themeBtn').innerHTML = `${ic(claro ? 'moon' : 'sun')}<span id="themeLabel">${claro ? 'Tema escuro' : 'Tema claro'}</span>`;
  $('#themeBtn2').innerHTML = ic(claro ? 'moon' : 'sun');
  store.set('tema', t);
  icones();
  aplicarTemaMapa();
  destruirGraficos();
  S.assinaturas.metricas = null;
  if (S.view === 'metricas') carregarMetricas();
}
const alternarTema = () => aplicarTema(S.tema === 'light' ? 'dark' : 'light');
$('#themeBtn').onclick = alternarTema;
$('#themeBtn2').onclick = alternarTema;

// ------------------------------------------------------------------ início
function iniciar() {
  S.tema = store.get('tema', 'light');
  document.documentElement.dataset.theme = S.tema;
  $$('#modoSeg button').forEach((b) => b.classList.toggle('active', b.dataset.modo === S.modo));
  renderChips();
  iniciarMapa(() => carregarLista(), (id) => selecionarUnidade(id));
  iniciarBot();
  aplicarTema(S.tema);
  icones();
  carregarMetricas();
  carregarLista().then(carregarTicket);

  let tick = 0;
  setInterval(() => {
    tick++;
    if (document.hidden) return;
    if (S.ticketId && (S.view === 'ticket' || tick % 2 === 0)) carregarTicket();
    if (S.view === 'descobrir') carregarLista();
    if (S.view === 'painel') carregarPainel();
    if (S.view === 'metricas' && tick % 3 === 0) carregarMetricas();
    if (S.view === 'ia' && tick % 5 === 0) carregarIA();
  }, 2000);
}
iniciar();
