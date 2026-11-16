/* Renderizacao das telas. Tudo que vem da API passa por esc() antes de ir para o HTML. */

// ------------------------------------------------------------------ DESCOBRIR
const CATEGORIAS = [['', 'Todos'], ['SAUDE', '🏥 Saúde'], ['BANCO', '🏦 Bancos'], ['GOVERNO', '🏛️ Público'], ['CARTORIO', '📜 Cartório'], ['RESTAURANTE', '🍽️ Comer']];

function renderChips() {
  $('#catChips').innerHTML = CATEGORIAS.map(([v, t]) =>
    `<button class="chip${(S.categoria || '') === v ? ' active' : ''}" data-cat="${v}">${t}</button>`).join('');
}

function renderLista() {
  const el = $('#lista');
  if (!S.lista.length) { el.innerHTML = `<div class="card card-pad muted">Nenhum local nesta categoria.</div>`; return; }
  const maxT = Math.max(...S.lista.map((e) => e.tempoPresencialMin || e.esperaMin), 1);
  const entrada = S.listaPronta ? '' : ' enter'; // anima so na primeira vez (a lista atualiza a cada 2s)
  S.listaPronta = true;
  el.innerHTML = S.lista.map((e, i) => {
    const cor = NIVEL_COR[e.nivel];
    const temLoc = e.tempoFilaCertaMin != null;
    const pres = e.tempoPresencialMin ?? e.esperaMin;
    const certa = e.tempoFilaCertaMin ?? e.esperaMin;
    return `<article class="ecard${entrada}${e.recomendado ? ' reco' : ''}${S.selecionado === e.id ? ' sel' : ''}" data-id="${e.id}" style="animation-delay:${i * 40}ms">
      <div class="ecard-top">
        <div class="emoji">${CAT[e.categoria] || '📍'}</div>
        <div><h3>${esc(e.nome)}</h3><div class="where">${esc(e.bairro)} · ${esc(e.categoriaRotulo)}${e.distanciaKm != null ? ' · ' + fmtKm(e.distanciaKm) : ''}</div></div>
        <div class="wait"><b style="color:${cor}">${e.esperaMin}<small style="font-size:.9rem"> min</small></b><span>de espera</span></div>
      </div>
      <div class="compare">
        <div class="bar-row"><span>Indo e esperando lá</span><div class="bar"><i style="width:${(pres / maxT) * 100}%;background:var(--muted)"></i></div><b>${fmtMin(pres)}</b></div>
        <div class="bar-row"><span style="color:var(--accent-text);font-weight:700">Com a Fila Certa</span><div class="bar"><i style="width:${(certa / maxT) * 100}%;background:var(--accent)"></i></div><b>${fmtMin(certa)}</b></div>
      </div>
      <div class="ecard-actions">
        <div>
          <div class="meta"><span>👥 ${e.pessoasNaFila} na fila</span><span>🪟 ${e.guiches - e.guichesLivres}/${e.guiches} guichês</span>${temLoc ? `<span>${MODOS[S.modo].icon} ${fmtMin(e.deslocamentoMin)}</span>` : ''}</div>
          ${temLoc && e.economiaMin > 0 ? `<div class="save-tag">⚡ você economiza ${fmtMin(e.economiaMin)}</div>` : ''}
        </div>
        <button class="btn btn-primary btn-sm" data-join="${e.id}">Entrar na fila</button>
      </div>
    </article>`;
  }).join('');
}

function abrirModalEntrar(id) {
  const e = S.lista.find((x) => x.id === id);
  if (!e) return;
  const temTicket = S.ticket && (S.ticket.status === 'AGUARDANDO' || S.ticket.status === 'CHAMADO');
  const root = $('#modalRoot');
  const saiEm = Math.max(0, e.esperaMin - (e.deslocamentoMin || 0) - 2);
  root.innerHTML = `<div class="overlay" id="overlay"><form class="modal" id="joinForm" role="dialog" aria-label="Entrar na fila">
    <div class="eyebrow">${CAT[e.categoria]} ${esc(e.categoriaRotulo)}</div>
    <h2>${esc(e.nome)}</h2>
    <p class="sub">${esc(e.endereco)} — ${esc(e.bairro)}</p>
    <div class="plan">
      <div><b style="color:${NIVEL_COR[e.nivel]}">${e.esperaMin}'</b><span>espera</span></div>
      <div><b>${fmtMin(e.deslocamentoMin)}</b><span>trajeto</span></div>
      <div><b style="color:var(--accent-text)">${fmtMin(saiEm)}</b><span>sair em</span></div>
    </div>
    ${temTicket ? `<p class="muted" style="font-size:.82rem;margin-bottom:12px">⚠️ Você já tem um ticket ativo. Este será um novo ticket.</p>` : ''}
    <div class="field"><label for="jNome">SEU NOME</label><input class="input" id="jNome" maxlength="60" required placeholder="Como devemos te chamar?" value="${esc(store.get('nome', ''))}"></div>
    <label class="check"><input type="checkbox" id="jPrio"> <span>Atendimento prioritário <span class="muted">(idoso, gestante, PcD)</span></span></label>
    <div class="modal-actions"><button type="button" class="btn btn-ghost" id="jCancel">Agora não</button><button class="btn btn-primary" type="submit">Garantir minha vez</button></div>
  </form></div>`;
  $('#jNome').focus();
  const fechar = () => { root.innerHTML = ''; };
  $('#jCancel').onclick = fechar;
  $('#overlay').addEventListener('mousedown', (ev) => { if (ev.target.id === 'overlay') fechar(); });
  $('#joinForm').onsubmit = async (ev) => {
    ev.preventDefault();
    const nome = $('#jNome').value.trim();
    if (!nome) return;
    const btn = ev.submitter || ev.target.querySelector('[type="submit"]'); btn.disabled = true;
    try {
      store.set('nome', nome);
      const t = await api(`/estabelecimentos/${id}/fila`, { method: 'POST', body: { nome, prioritario: $('#jPrio').checked, latitude: S.loc.lat, longitude: S.loc.lng, modo: S.modo } });
      beep([660, 990]);
      fechar();
      definirTicket(t, true);
      toast(`Ticket ${t.codigo} emitido! Vamos te avisar a hora de sair.`, 'ok', '🎟️');
      irPara('ticket');
    } catch (err) { toast(err.message, 'err', '⚠️'); btn.disabled = false; }
  };
}

// ------------------------------------------------------------------ MEU TICKET
const TITULOS = {
  AGUARDE: ['Relaxa, você tem tempo livre', 'ok'],
  PREPARE_SE: ['Prepare-se para sair', 'warn'],
  SAIA_AGORA: ['Hora de sair!', 'bad'],
  SUA_VEZ: ['É a sua vez!', 'bad'],
  CONCLUIDO: ['Atendimento concluído', 'ok'],
  CANCELADO: ['Você saiu da fila', 'muted'],
  AUSENTE: ['Você perdeu a vez', 'bad'],
};

function renderTicket() {
  const root = $('#view-ticket');
  const t = S.ticket;
  if (!t) {
    root.innerHTML = `<div class="card empty"><div class="big">🎟️</div><h2>Você ainda não está em nenhuma fila</h2>
      <p>Escolha um local no radar, entre na fila e relaxe. A gente calcula a hora exata de você sair.</p>
      <button class="btn btn-primary btn-big" data-go="descobrir">📡 Encontrar uma fila</button></div>`;
    return;
  }
  const ativo = t.status === 'AGUARDANDO' || t.status === 'CHAMADO';
  const [titulo] = TITULOS[t.aviso] || ['Seu ticket', ''];
  const modo = MODOS[t.modo] || MODOS.TRANSPORTE;

  // anel de progresso
  const C = 2 * Math.PI * 48;
  let prog = 1, centro = '<b>✓</b><span>feito</span>';
  if (t.status === 'AGUARDANDO') {
    prog = S.espera0 > 0 ? Math.min(1, Math.max(0.04, 1 - t.esperaMin / S.espera0)) : 0.04;
    if (t.aviso === 'SAIA_AGORA') centro = '<b>JÁ</b><span>saia agora</span>';
    else if (t.saiEmMin != null) centro = `<b>${t.saiEmMin}</b><span>min p/ sair</span>`;
    else centro = `<b>${t.esperaMin}</b><span>min p/ vez</span>`;
  } else if (t.status === 'CHAMADO') {
    centro = `<b>${t.guiche}</b><span>guichê</span>`;
  }

  // jornada: livre -> a caminho -> folga
  let jornada = '';
  if (t.status === 'AGUARDANDO' && t.deslocamentoMin != null) {
    const livre = t.saiEmMin || 0;
    const ida = t.deslocamentoMin;
    const folga = Math.max(0, t.esperaMin - livre - ida);
    const atraso = Math.max(0, ida + 2 - t.esperaMin);
    jornada = `<div class="journey"><h3>Seu plano até a vez</h3>
      <div class="track">
        ${livre > 0 ? `<div class="seg-free" style="flex:${livre}">livre ${livre}'</div>` : ''}
        <div class="seg-go" style="flex:${Math.max(ida, 1)}">${modo.icon} ${ida}'</div>
        ${folga > 0 ? `<div class="seg-buf" style="flex:${folga}"></div>` : ''}
      </div>
      <div class="track-legend"><span>agora</span><span>${atraso > 0 ? `⚠️ você chega ~${atraso} min depois da vez` : 'chegada na hora da sua vez'}</span><span>sua vez</span></div></div>`;
  }

  const resumo = t.status === 'ATENDIDO' ? `<div class="summary"><span class="eyebrow">Resumo</span>
      <b>Você esperou ${fmtMin(t.esperouMin)}${t.economizouMin ? ` e poupou ${fmtMin(t.economizouMin)} de fila no balcão` : ''}.</b>
      <span class="muted">Obrigado por usar a Fila Certa! Seu atendimento ajuda a deixar a previsão ainda mais certa.</span></div>` : '';

  const acoes = t.status === 'AGUARDANDO'
    ? `<button class="btn btn-danger" data-act="cancelar">Sair da fila</button><button class="btn btn-ghost" data-act="simular" id="btnSim">▶ Simular trajeto</button>`
    : (!ativo ? `<button class="btn btn-primary" data-go="descobrir">📡 Nova fila</button>` : '');

  root.innerHTML = `
    <div class="view-head"><div><h1>Seu <em>ticket</em></h1><p>Atualizado ao vivo. Pode fechar o app e fazer outra coisa: a gente avisa na hora certa.</p></div></div>
    <div class="ticket-grid">
      <div class="card pass state-${t.aviso}">
        <div class="stub">
          <span class="eyebrow">Senha</span>
          <div class="code">${esc(t.codigo)}</div>
          <div class="who">${esc(t.nomeCliente)}</div>
          <div>${CAT[t.categoria] || ''} ${esc(t.estabelecimentoNome)}</div>
          ${t.prioritario ? '<span class="tag prio">Prioritário</span>' : ''}
          <span class="tag">${esc(t.status)}</span>
        </div>
        <div class="body">
          <div class="headline">
            <div class="ring-wrap"><svg class="ring-svg" viewBox="0 0 120 120"><circle class="ring-bg" cx="60" cy="60" r="48"/><circle class="ring-fg" cx="60" cy="60" r="48" stroke-dasharray="${C}" stroke-dashoffset="${C * (1 - prog)}"/></svg><div class="center">${centro}</div></div>
            <div><h2>${titulo}</h2><p>${esc(t.mensagem)}</p></div>
          </div>
          ${t.status === 'AGUARDANDO' ? `<div class="stats">
            <div class="stat"><b>${t.posicao}º</b><span>sua posição</span></div>
            <div class="stat"><b>${fmtMin(t.esperaMin)}</b><span>até a sua vez</span></div>
            <div class="stat"><b>${fmtMin(t.deslocamentoMin)}</b><span>de trajeto${t.distanciaKm != null ? ' · ' + fmtKm(t.distanciaKm) : ''}</span></div>
          </div>` : ''}
          ${jornada}${resumo}
          <div class="actions">${acoes}</div>
        </div>
      </div>
      <div class="side-stack">
        <div class="card card-pad">
          <div class="chart-head"><h2>Trajeto</h2>
            <div class="seg" id="modoTicket">${Object.entries(MODOS).map(([k, m]) => `<button data-modo="${k}" class="${t.modo === k ? 'active' : ''}" title="${m.nome}">${m.icon}</button>`).join('')}</div></div>
          <div class="map-mini"><svg id="mapMini" aria-label="Mapa do trajeto"></svg></div>
          <p class="muted" style="font-size:.78rem;margin-top:10px">${ativo ? 'Mude o modo de deslocamento ou use “Simular trajeto” para ver a hora de sair se atualizar.' : 'Ticket encerrado.'}</p>
        </div>
      </div>
    </div>`;

  const e = S.lista.find((x) => x.id === t.estabelecimentoId) || S.estabTicket;
  if (e) desenharRota($('#mapMini'), S.loc, { lat: e.latitude, lng: e.longitude }, e.nome, CAT[e.categoria] || '📍');
}

// ------------------------------------------------------------------ PAINEL
function renderPainel(p) {
  const root = $('#painelBody');
  const est = p.estabelecimento;
  const livres = p.guiches.filter((g) => !g.atual).length;
  root.innerHTML = `
    <div class="painel-top">
      <div class="mini-kpis">
        <div class="stat"><b>${p.fila.length}</b><span>na fila</span></div>
        <div class="stat"><b>${livres}/${est.guiches}</b><span>guichês livres</span></div>
        <div class="stat"><b>${fmtMin(est.esperaMin)}</b><span>espera prevista</span></div>
        <div class="stat"><b>${p.atendidosHoje}</b><span>atendidos hoje</span></div>
      </div>
      <button class="btn btn-primary btn-big" data-act="chamar" ${p.fila.length === 0 || livres === 0 ? 'disabled' : ''}>📣 Chamar próximo</button>
    </div>
    <div class="counters">${p.guiches.map((g) => g.atual
      ? `<div class="gcard busy"><div class="n"><span>Guichê ${g.numero}</span><span>● atendendo</span></div><div class="cur">${esc(g.atual.codigo)}</div><div>${esc(g.atual.nomeCliente)}</div>
          <div class="row"><button class="btn btn-primary btn-sm" data-act="finalizar" data-id="${g.atual.id}">Finalizar</button><button class="btn btn-ghost btn-sm" data-act="ausente" data-id="${g.atual.id}">Ausente</button></div></div>`
      : `<div class="gcard free"><div class="n"><span>Guichê ${g.numero}</span><span>○ livre</span></div><div class="cur">Disponível</div></div>`).join('')}
    </div>
    <div class="card card-pad">
      <div class="chart-head"><h2>Fila de espera</h2><span class="muted" style="font-size:.82rem">prioritários passam na frente</span></div>
      <div class="qlist">${p.fila.length ? p.fila.map((t) => `<div class="qitem">
        <span class="pos">${t.posicao}º</span><b class="num">${esc(t.codigo)}</b>
        <span>${esc(t.nomeCliente)} ${t.prioritario ? '<span class="tag prio">Prior.</span>' : ''}<br><small>${t.distanciaKm != null ? fmtKm(t.distanciaKm) + ' · ' + MODOS[t.modo].icon + ' ' + fmtMin(t.deslocamentoMin) : 'sem localização'}</small></span>
        <span class="cd">~${fmtMin(t.esperaMin)}</span></div>`).join('') : '<p class="muted">Ninguém aguardando. 🎉</p>'}</div>
    </div>`;
}

// ------------------------------------------------------------------ METRICAS
function renderMetricas(m) {
  const root = $('#metricasBody');
  const maxH = Math.max(...m.esperaPorHora.map((h) => h.esperaMediaMin), 1);
  const maxE = Math.max(...m.estabelecimentos.map((e) => e.esperaMediaMin), 1);
  const maxD = Math.max(...m.estabelecimentos.map((e) => e.distanciaMediaClientesKm), 1);
  const sel = m.estabelecimentos.slice().sort((a, b) => a.nome.localeCompare(b.nome));
  root.innerHTML = `
    <div class="kpis">
      <div class="kpi" style="--c:var(--accent)"><span class="eyebrow">Atendimentos</span><b>${m.atendimentosTotal.toLocaleString('pt-BR')}</b><p>base histórica + ao vivo</p></div>
      <div class="kpi" style="--c:var(--warn)"><span class="eyebrow">Espera média</span><b>${fmtDec(m.esperaMediaMin)}<small>min</small></b><p>entre entrar e ser chamado</p></div>
      <div class="kpi" style="--c:var(--accent-2)"><span class="eyebrow">Distância média</span><b>${fmtDec(m.distanciaMediaClientesKm)}<small>km</small></b><p>de onde o cliente entra na fila</p></div>
      <div class="kpi" style="--c:var(--ok)"><span class="eyebrow">Na fila agora</span><b>${m.pessoasEmFilaAgora}</b><p>${m.ticketsAtivos} tickets ativos</p></div>
    </div>
    <div class="charts">
      <div class="card card-pad">
        <div class="chart-head"><h2>⏱️ Espera média por horário</h2>
          <select class="input" id="metEstab" style="max-width:230px;padding:9px 12px"><option value="">Todos os locais</option>${sel.map((e) => `<option value="${e.id}" ${String(e.id) === S.metricasEstab ? 'selected' : ''}>${esc(e.nome)}</option>`).join('')}</select></div>
        <div class="vbars">${m.esperaPorHora.map((h) => `<div class="vbar${h.hora === m.melhorHorario ? ' best' : ''}${h.hora === m.piorHorario ? ' worst' : ''}">
          <i style="height:${(h.esperaMediaMin / maxH) * 100}%"><em>${h.amostras ? Math.round(h.esperaMediaMin) : ''}</em></i><span>${h.hora}h</span></div>`).join('')}</div>
        <div class="insights">
          ${m.melhorHorario != null ? `<div class="insight">🟢 Melhor horário: <b>${m.melhorHorario}h</b></div>` : ''}
          ${m.piorHorario != null ? `<div class="insight">🔴 Evite: <b>${m.piorHorario}h</b></div>` : ''}
          <div class="insight">🧠 Previsor: <b>${esc(m.previsor)}</b></div>
        </div>
      </div>
      <div class="card card-pad">
        <div class="chart-head"><h2>📍 Espera por local</h2></div>
        <div class="hbars">${m.estabelecimentos.map((e) => `<div class="hrow"><div class="top"><span>${esc(e.nome)}</span><span>${fmtDec(e.esperaMediaMin)} min</span></div>
          <div class="bar"><i style="width:${(e.esperaMediaMin / maxE) * 100}%;background:${e.esperaMediaMin > maxE * 0.66 ? 'var(--bad)' : e.esperaMediaMin > maxE * 0.33 ? 'var(--warn)' : 'var(--ok)'}"></i></div></div>`).join('')}</div>
        <div class="chart-head" style="margin-top:26px"><h2>🧭 De onde vêm os clientes</h2></div>
        <div class="hbars">${m.estabelecimentos.slice().sort((a, b) => b.distanciaMediaClientesKm - a.distanciaMediaClientesKm).map((e) => `<div class="hrow"><div class="top"><span>${esc(e.nome)}</span><span>${fmtDec(e.distanciaMediaClientesKm)} km</span></div>
          <div class="bar"><i style="width:${(e.distanciaMediaClientesKm / maxD) * 100}%;background:var(--accent-2)"></i></div></div>`).join('')}</div>
      </div>
    </div>`;
}
