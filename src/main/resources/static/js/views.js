/* Renderização das telas. Tudo que vem da API passa por esc() antes de ir para o HTML. */

// ------------------------------------------------------------------ DESCOBRIR
const CATEGORIAS_ROTULO = { SAUDE: 'Saúde', BANCO: 'Bancos', GOVERNO: 'Serviços públicos', CARTORIO: 'Cartórios', RESTAURANTE: 'Restaurantes' };

/** Abas só das categorias que existem nos dados carregados. */
function renderChips() {
  const cats = (S.catsDisponiveis || Object.keys(CATEGORIAS_ROTULO)).map((c) => [c, CATEGORIAS_ROTULO[c] || c]);
  $('#catChips').innerHTML = [['', 'Todas'], ...cats].map(([v, t]) =>
    `<button class="${(S.categoria || '') === v ? 'active' : ''}" data-cat="${v}">${t}</button>`).join('');
}

function renderLista() {
  if (!mudou('lista', [S.lista, S.modo, S.selecionado, S.busca])) return;
  const el = $('#lista');
  const lista = visiveis();
  $('#contagem').textContent = S.busca.trim()
    ? `${lista.length} de ${S.lista.length} unidades`
    : 'ordenadas por deslocamento + espera';
  if (!lista.length) {
    el.innerHTML = S.busca.trim()
      ? `<div class="panel empty-sm"><b>Nenhuma unidade encontrada para "${esc(S.busca.trim())}"</b><p>Confira o nome ou procure pelo bairro. A busca ignora acentos.</p><button class="btn btn-secondary btn-sm" data-act="limpar-busca">Limpar busca</button></div>`
      : '<div class="panel panel-pad muted">Nenhuma unidade nesta categoria.</div>';
    return;
  }
  const maxT = Math.max(...lista.map((e) => e.tempoPresencialMin ?? e.esperaMin), 1);
  el.innerHTML = lista.map((e) => {
    const temLoc = e.tempoFilaCertaMin != null;
    const pres = e.tempoPresencialMin ?? e.esperaMin;
    const certa = e.tempoFilaCertaMin ?? e.esperaMin;
    return `<article class="eitem${S.selecionado === e.id ? ' sel' : ''}" data-id="${e.id}">
      <div class="eitem-head">
        <div class="cat-ic">${ic(CAT_ICON[e.categoria] || 'map-pin')}</div>
        <div>
          ${e.recomendado ? '<span class="badge primary">Melhor opção agora</span>' : ''}
          <h3>${esc(e.nome)}</h3>
          <div class="where">${esc(e.bairro)} · ${esc(e.categoriaRotulo)}${e.distanciaKm != null ? ' · ' + fmtKm(e.distanciaKm) : ''}</div>
        </div>
        <div class="wait"><b class="lvl-${e.nivel}">${e.esperaMin}<small> min</small></b><span>de espera</span></div>
      </div>
      <div class="cmp">
        <div class="bar-row"><span>Ir e esperar lá</span><div class="bar"><i style="width:${(pres / maxT) * 100}%"></i></div><b>${fmtMin(pres)}</b></div>
        <div class="bar-row us"><span>Com a Fila Certa</span><div class="bar"><i style="width:${(certa / maxT) * 100}%"></i></div><b>${fmtMin(certa)}</b></div>
      </div>
      <div class="eitem-foot">
        <div>
          <div class="meta"><span>${ic('users')}${e.pessoasNaFila} na fila</span><span>${ic('monitor')}${e.guiches - e.guichesLivres}/${e.guiches} guichês</span>${temLoc ? `<span>${ic(MODOS[S.modo].icon)}${fmtMin(e.deslocamentoMin)} de trajeto</span>` : ''}</div>
          ${temLoc && e.economiaMin > 0 ? `<div class="saving">${ic('timer')}Economia de ${fmtMin(e.economiaMin)}</div>` : ''}
        </div>
        <button class="btn btn-primary btn-sm" data-join="${e.id}">Entrar na fila</button>
      </div>
    </article>`;
  }).join('');
  icones();
}

function abrirModalEntrar(id) {
  const e = S.lista.find((x) => x.id === id);
  if (!e) return;
  const temTicket = S.ticket && (S.ticket.status === 'AGUARDANDO' || S.ticket.status === 'CHAMADO');
  const root = $('#modalRoot');
  const saiEm = Math.max(0, e.esperaMin - (e.deslocamentoMin || 0) - 2);
  root.innerHTML = `<div class="overlay" id="overlay"><form class="modal" id="joinForm" role="dialog" aria-label="Entrar na fila">
    <div class="modal-head">
      <span class="eyebrow">${esc(e.categoriaRotulo)}</span>
      <h2>${esc(e.nome)}</h2>
      <p>${esc(e.endereco)} — ${esc(e.bairro)}</p>
    </div>
    <div class="modal-body">
      <div class="plan">
        <div><b class="lvl-${e.nivel}">${e.esperaMin} min</b><span>Espera</span></div>
        <div><b>${fmtMin(e.deslocamentoMin)}</b><span>Trajeto</span></div>
        <div><b style="color:var(--primary)">${fmtMin(saiEm)}</b><span>Sair em</span></div>
      </div>
      ${temTicket ? `<p class="note">${ic('info')}<span>Você já tem um ticket ativo. Este será um novo ticket.</span></p>` : ''}
      <div class="field"><label for="jNome">Seu nome</label><input class="input" id="jNome" maxlength="60" required placeholder="Como devemos te chamar?" value="${esc(store.get('nome', ''))}"></div>
      <label class="check"><input type="checkbox" id="jPrio"><span><b>Atendimento prioritário</b><br><span class="muted">Idosos, gestantes, pessoas com deficiência e lactantes.</span></span></label>
    </div>
    <div class="modal-foot"><button type="button" class="btn btn-secondary" id="jCancel">Cancelar</button><button class="btn btn-primary" type="submit" id="jOk">Garantir minha vez</button></div>
  </form></div>`;
  icones();
  $('#jNome').focus();
  const fechar = () => { root.innerHTML = ''; };
  $('#jCancel').onclick = fechar;
  $('#overlay').addEventListener('mousedown', (ev) => { if (ev.target.id === 'overlay') fechar(); });
  // A chave identifica este pedido: se a resposta se perder, reenviar (ou consultar) nao cria um segundo ticket.
  const chave = window.crypto && crypto.randomUUID ? crypto.randomUUID() : null;
  const concluir = (t) => {
    beep([660, 990]);
    fechar();
    S.assinaturas.ticket = null;
    definirTicket(t, true);
    toast(`Ticket ${t.codigo} emitido. Avisaremos a hora de sair.`, 'ok');
    irPara('ticket');
  };
  $('#joinForm').onsubmit = async (ev) => {
    ev.preventDefault();
    const nome = $('#jNome').value.trim();
    if (!nome) return;
    const btn = $('#jOk'); btn.disabled = true; btn.textContent = 'Enviando…';
    store.set('nome', nome);
    try {
      const t = await api(`/estabelecimentos/${id}/fila`, { method: 'POST', body: { nome, prioritario: $('#jPrio').checked, latitude: S.loc.lat, longitude: S.loc.lng, modo: S.modo, chave } });
      concluir(t);
    } catch (err) {
      // O servidor pode ter criado o ticket e a resposta ter se perdido: confere pela chave antes de mostrar erro.
      let criado = null;
      if (chave) { try { criado = await api(`/tickets/${chave}`, { timeout: 6000 }); } catch (_) { /* nao existe */ } }
      if (criado) { concluir(criado); return; }
      toast(`${err.message} Seu pedido não foi registrado; tente novamente.`, 'err');
      btn.disabled = false; btn.textContent = 'Garantir minha vez';
    }
  };
}

// ------------------------------------------------------------------ MEU TICKET
const TITULOS = {
  AGUARDE: 'Você tem tempo livre',
  PREPARE_SE: 'Prepare-se para sair',
  SAIA_AGORA: 'Hora de sair',
  SUA_VEZ: 'É a sua vez',
  CONCLUIDO: 'Atendimento concluído',
  CANCELADO: 'Você saiu da fila',
  AUSENTE: 'Você perdeu a vez',
};
const STATUS_ROTULO = { AGUARDANDO: 'Aguardando', CHAMADO: 'Chamado', ATENDIDO: 'Atendido', CANCELADO: 'Cancelado', AUSENTE: 'Ausente' };
const STATUS_COR = { AGUARDANDO: 'primary', CHAMADO: 'ok', ATENDIDO: 'ok', CANCELADO: '', AUSENTE: 'bad' };

function renderTicket() {
  const t = S.ticket;
  const vazio = $('#ticketEmpty'), layout = $('#ticketLayout');
  if (!t) {
    layout.classList.add('hidden');
    vazio.classList.remove('hidden');
    vazio.innerHTML = `<div class="empty-ic">${ic('ticket')}</div><h2>Você não está em nenhuma fila</h2>
      <p>Escolha uma unidade no mapa e entre na fila. Calculamos a hora certa de você sair.</p>
      <button class="btn btn-primary btn-lg" data-go="descobrir">${ic('map-pin')}Encontrar uma fila</button>`;
    icones();
    return;
  }
  vazio.classList.add('hidden');
  layout.classList.remove('hidden');
  if (!MAPA.mini) iniciarMapaMini(); else MAPA.mini.invalidateSize();

  $$('#modoTicket button').forEach((b) => b.classList.toggle('active', b.dataset.modo === t.modo));
  const est = S.lista.find((x) => x.id === t.estabelecimentoId) || S.estabTicket;

  if (mudou('ticket', [t, S.espera0])) {
    const ativo = t.status === 'AGUARDANDO' || t.status === 'CHAMADO';
    const modo = MODOS[t.modo] || MODOS.TRANSPORTE;
    let big = '—', rotulo = '';
    if (t.status === 'AGUARDANDO') {
      if (t.aviso === 'SAIA_AGORA') { big = 'Agora'; rotulo = 'saia de casa'; }
      else if (t.saiEmMin != null) { big = t.saiEmMin; rotulo = 'min para sair'; }
      else { big = t.esperaMin; rotulo = 'min até a sua vez'; }
    } else if (t.status === 'CHAMADO') { big = t.guiche; rotulo = 'guichê'; }
    else if (t.status === 'ATENDIDO') { big = t.esperouMin ?? '—'; rotulo = 'min de espera'; }

    let jornada = '';
    if (t.status === 'AGUARDANDO' && t.deslocamentoMin != null) {
      const livre = t.saiEmMin || 0, ida = t.deslocamentoMin;
      const folga = Math.max(0, t.esperaMin - livre - ida);
      const atraso = Math.max(0, ida + 2 - t.esperaMin);
      jornada = `<div class="journey"><h3>Plano até a sua vez</h3>
        <div class="track">
          ${livre > 0 ? `<div class="seg-free" style="flex:${livre}">Livre ${livre} min</div>` : ''}
          <div class="seg-go" style="flex:${Math.max(ida, 1)}">${modo.nome} ${ida} min</div>
          ${folga > 0 ? `<div class="seg-buf" style="flex:${folga}"></div>` : ''}
        </div>
        <div class="track-legend"><span>Agora</span><span>${atraso > 0 ? `Você chegaria cerca de ${atraso} min depois da vez` : 'Chegada na hora da sua vez'}</span><span>Sua vez</span></div></div>`;
    }
    const resumo = t.status === 'ATENDIDO'
      ? `<div class="summary">Você esperou <b>${fmtMin(t.esperouMin)}</b>${t.economizouMin ? ` e evitou <b>${fmtMin(t.economizouMin)}</b> de fila no balcão` : ''}. Obrigado por usar a Fila Certa.</div>` : '';
    const acoes = t.status === 'AGUARDANDO'
      ? `<button class="btn btn-danger" data-act="cancelar">Sair da fila</button><button class="btn btn-secondary" data-act="simular" id="btnSim">${ic('play')}Simular trajeto</button>`
      : (!ativo ? `<button class="btn btn-primary" data-go="descobrir">${ic('map-pin')}Nova fila</button>` : '');

    $('#ticketMain').innerHTML = `
      <div class="panel state-${t.aviso}">
        <div class="pass-head">
          <div><span class="eyebrow">Senha</span><div class="pass-code">${esc(t.codigo)}</div></div>
          <div class="pass-who"><b>${esc(t.nomeCliente)}</b><span>${esc(t.estabelecimentoNome)}</span></div>
          <div style="display:flex;gap:6px;flex-wrap:wrap">${t.prioritario ? '<span class="badge warn">Prioritário</span>' : ''}<span class="badge ${STATUS_COR[t.status]}">${STATUS_ROTULO[t.status]}</span></div>
        </div>
        <div class="leave">
          <div class="leave-num"><b>${big}</b><span>${rotulo}</span></div>
          <div class="leave-txt"><h2>${TITULOS[t.aviso] || 'Seu ticket'}</h2><p>${esc(t.mensagem)}</p></div>
        </div>
        ${resumo}
        ${t.status === 'AGUARDANDO' ? `<div class="stats">
          <div class="stat"><b>${t.posicao}º</b><span>Posição</span></div>
          <div class="stat"><b>${fmtMin(t.esperaMin)}</b><span>Até a sua vez</span></div>
          <div class="stat"><b>${fmtMin(t.deslocamentoMin)}</b><span>Trajeto${t.distanciaKm != null ? ' · ' + fmtKm(t.distanciaKm) : ''}</span></div>
        </div>` : ''}
        ${jornada}
        ${acoes ? `<div class="pass-actions">${acoes}</div>` : ''}
      </div>`;
    $('#rotaInfo').textContent = ativo ? 'Troque o modo de deslocamento ou use "Simular trajeto" para ver a hora de sair mudar.' : 'Ticket encerrado.';
    icones();
  }
  if (est) atualizarMapaMini(est);
}

// ------------------------------------------------------------------ PAINEL
function renderPainel(p) {
  if (!mudou('painel', p)) return;
  const est = p.estabelecimento;
  const livres = p.guiches.filter((g) => !g.atual).length;
  $('#painelBody').innerHTML = `
    <div class="kpi-row">
      <div class="stat"><b>${p.fila.length}</b><span>Na fila</span></div>
      <div class="stat"><b>${livres}/${est.guiches}</b><span>Guichês livres</span></div>
      <div class="stat"><b>${fmtMin(est.esperaMin)}</b><span>Espera prevista</span></div>
      <div class="stat"><b>${p.atendidosHoje}</b><span>Atendidos hoje</span></div>
      <button class="btn btn-primary btn-lg" data-act="chamar" ${p.fila.length === 0 || livres === 0 ? 'disabled' : ''}>${ic('megaphone')}Chamar próximo</button>
    </div>
    <div class="counters">${p.guiches.map((g) => g.atual
      ? `<div class="gcard busy"><div class="n"><span>Guichê ${g.numero}</span><span class="badge ok">Atendendo</span></div><div class="cur">${esc(g.atual.codigo)}</div><div>${esc(g.atual.nomeCliente)}</div>
          <div class="row"><button class="btn btn-primary btn-sm" data-act="finalizar" data-id="${g.atual.id}">Finalizar</button><button class="btn btn-secondary btn-sm" data-act="ausente" data-id="${g.atual.id}">Ausente</button></div></div>`
      : `<div class="gcard free"><div class="n"><span>Guichê ${g.numero}</span><span class="badge">Livre</span></div><div class="cur">Disponível</div></div>`).join('')}
    </div>
    <div class="panel">
      <div class="panel-head"><h2>Fila de espera</h2><span class="muted" style="font-size:.8rem">Prioritários passam na frente</span></div>
      ${p.fila.length ? `<div class="table-wrap"><table class="tbl"><thead><tr><th>Pos.</th><th>Senha</th><th>Cliente</th><th>Origem</th><th class="r">Espera prevista</th></tr></thead><tbody>
        ${p.fila.map((t) => `<tr><td class="num">${t.posicao}º</td><td class="code">${esc(t.codigo)}</td>
          <td>${esc(t.nomeCliente)} ${t.prioritario ? '<span class="badge warn">Prioritário</span>' : ''}</td>
          <td>${t.distanciaKm != null ? `${fmtKm(t.distanciaKm)} · ${MODOS[t.modo].nome}` : '—'}${t.deslocamentoMin != null ? `<small>${fmtMin(t.deslocamentoMin)} de trajeto</small>` : ''}</td>
          <td class="r num">${fmtMin(t.esperaMin)}</td></tr>`).join('')}</tbody></table></div>`
        : '<div class="panel-body muted">Ninguém aguardando no momento.</div>'}
    </div>`;
  icones();
}

// ------------------------------------------------------------------ PREVISÃO (ML)
const SETOR_ROTULO = { CLINICO: 'Clínico geral', PEDIATRIA: 'Pediatria', ORTOPEDIA: 'Ortopedia', EMERGENCIA: 'Emergência', TRIAGEM: 'Triagem', ATENDIMENTO: 'Atendimento', GINECOLOGIA: 'Ginecologia', ODONTOLOGIA: 'Odontologia', OUTRO: 'Outro' };
const UNIDADE_ROTULO = { 'ENGENHO NOVO': 'PA Engenho Novo', IMPERIAL: 'PA Imperial', CAMARGO: 'PA Camargo', MUTINGA: 'PA Mutinga', PAA: 'PA Adulto (PAA)', PAI: 'PA Infantil (PAI)', HMB: 'HMB', OUTRA: 'Outra unidade' };

/** "PRONTO ATENDIMENTO CAMARGO- FAST TRACK - CLINICO" -> "Fast track · clinico" (sem repetir a unidade) */
function detalheLocal(p) {
  const partes = p.local.split('-').map((x) => x.trim()).filter(Boolean).slice(1)
    .filter((x) => !['PAA', 'PAI', p.unidade].includes(x.toUpperCase()));
  const t = (partes.length ? partes.join(' · ') : SETOR_ROTULO[p.setor] || p.setor).toLowerCase();
  return t.charAt(0).toUpperCase() + t.slice(1);
}

function chipProb(p, limiar) {
  if (p.probLonga == null) return '<span class="prob" title="Sem modelo carregado">—</span>';
  const pct = Math.round(p.probLonga * 100);
  const cls = pct >= 50 ? 'hi' : pct >= 20 ? 'mid' : 'lo';
  return `<span class="prob ${cls}" title="Chance de a espera passar de ${limiar} min">${pct}%</span>`;
}

function tendencia(t) {
  if (t == null || t === 0) return '<span class="trend muted">estável</span>';
  return t > 0 ? `<span class="trend t-bad">${ic('trending-up')} +${t}</span>` : `<span class="trend t-ok">${ic('trending-down')} ${t}</span>`;
}

/** Compara a espera atual com a tipica do horario (modelo de perfil, dados reais). */
function notaTipico(p) {
  if (p.tipicoMin == null) return '';
  const acima = p.esperaAtualMin > p.tipicoMin * 1.5 + 5, abaixo = p.esperaAtualMin <= p.tipicoMin * 0.5 && p.tipicoMin >= 6;
  const tag = acima ? ' <span class="badge bad">acima do normal</span>' : abaixo ? ' <span class="badge ok">abaixo do normal</span>' : '';
  return `<small>típico neste horário: ${p.tipicoMin} min${tag}</small>`;
}

function blocoMetricas(titulo, sub, met) {
  return `<h2 class="section-title">${titulo} <small>${sub}</small></h2>
      <div class="panel"><div class="stats" style="border-bottom:0;grid-template-columns:repeat(auto-fit,minmax(150px,1fr))">
        <div class="stat"><b>${fmtDec(+met.mae_min.toFixed(1))} min</b><span>Erro médio</span></div>
        <div class="stat"><b>${fmtDec(+met.mae_baseline_min.toFixed(1))} min</b><span>Erro do baseline</span></div>
        <div class="stat"><b>${fmtDec(+met.r2.toFixed(2))}</b><span>R² (regressão)</span></div>
        <div class="stat"><b>${fmtDec(+met.recall.toFixed(2))}</b><span>Recall (longa)</span></div>
        <div class="stat"><b>${fmtDec(+met.f1.toFixed(2))}</b><span>F1 (longa)</span></div>
        <div class="stat"><b>${fmtDec(+met.auc.toFixed(2))}</b><span>ROC AUC</span></div></div></div>`;
}

function renderIA(v) {
  if (!mudou('ia', v)) return;
  const root = $('#iaBody');
  const m = v.meta;
  const fmtHora = (s) => (s ? new Date(s).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—');
  if (!v.previsoes.length) {
    root.innerHTML = `<div class="panel empty"><div class="empty-ic">${ic('activity')}</div><h2>Ainda sem leituras de Barueri</h2>
      <p>O aplicativo consulta o painel da Prefeitura a cada 5 minutos. Use "Coletar agora" para buscar a primeira leitura.</p></div>`;
    icones();
    return;
  }
  const met = m.metricas || {};
  const porUnidade = {};
  v.previsoes.forEach((p) => { (porUnidade[p.unidade] = porUnidade[p.unidade] || []).push(p); });
  const semTendencia = v.previsoes.some((p) => p.origem === 'modelo-sem-tendencia');
  const corNivel = { VERDE: 'var(--ok)', AMARELO: 'var(--warn)', VERMELHO: 'var(--bad)' };

  root.innerHTML = `
    ${m.aviso ? `<div class="banner">${ic('triangle-alert')}<span>${esc(m.aviso)}</span></div>` : ''}
    <div class="meta-row">
      <span>${ic('clock')}Leitura de ${fmtHora(m.ultimaLeitura)}</span>
      <span>${ic('database')}${m.leiturasTotal.toLocaleString('pt-BR')} leituras</span>
      <span>${ic('timer')}Previsão para ${m.horizonteMin} min</span>
      <span>${ic('gauge')}Espera longa: ${m.limiarLongaMin} min ou mais</span>
      ${semTendencia ? `<span title="O aplicativo precisa de cerca de 15 min de coleta para medir a tendência">${ic('trending-up')}Acumulando histórico</span>` : ''}
      ${m.perfilAtivo ? `<span>${ic('database')}Espera típica: dados reais ${esc((m.perfilFonte || '').replace('real ', ''))}</span>` : ''}
      <a href="/api/ml/dados.csv" download>${ic('download')}Baixar CSV coletado</a>
    </div>

    <div class="panel alert-panel ${v.alertas.length ? 'on' : ''}">
      <div class="panel-head"><h2>${v.alertas.length ? 'Risco de espera longa' : 'Nenhum setor em risco agora'}</h2><span class="muted" style="font-size:.8rem">Chance de 50% ou mais de passar de ${m.limiarLongaMin} min</span></div>
      ${v.alertas.length ? `<div class="table-wrap"><table class="tbl"><tbody>${v.alertas.map((p) => `<tr><td><b>${esc(UNIDADE_ROTULO[p.unidade] || p.unidade)}</b><small>${esc(detalheLocal(p))}</small></td>
        <td class="r num">${p.esperaAtualMin} <span class="muted">→</span> <b>${p.previstoMin} min</b></td><td class="r">${chipProb(p, m.limiarLongaMin)}</td></tr>`).join('')}</tbody></table></div>`
        : '<div class="panel-body muted">Quando um setor passar de 50% de chance, ele aparece aqui para o gestor remanejar equipe.</div>'}
    </div>

    <h2 class="section-title">Onde vale a pena ir agora</h2>
    <div class="reco-grid">${Object.entries(v.recomendacoes).map(([setor, lista]) => `
      <div class="panel"><div class="panel-head"><h2>${esc(SETOR_ROTULO[setor] || setor)}</h2></div>
        ${lista.map((p, i) => `<div class="reco-row${i === 0 ? ' best' : ''}"><span class="rk">${i + 1}º</span>
          <span class="nm">${esc(UNIDADE_ROTULO[p.unidade] || p.unidade)}<span class="det">${esc(detalheLocal(p))}</span></span>
          <span class="vl" style="color:${corNivel[p.nivel]}">${p.previstoMin}<small>min</small></span>${chipProb(p, m.limiarLongaMin)}</div>`).join('')}
      </div>`).join('')}</div>

    <h2 class="section-title">Todas as unidades <small>espera agora → prevista em ${m.horizonteMin} min</small></h2>
    <div class="units-grid">${Object.entries(porUnidade).map(([u, linhas]) => {
      const max = Math.max(30, ...linhas.map((p) => Math.max(p.esperaAtualMin, p.previstoMin)));
      return `<div class="panel"><div class="panel-head"><h2>${esc(UNIDADE_ROTULO[u] || u)}</h2></div>
        <div class="table-wrap"><table class="tbl"><tbody>${linhas.map((p) => `<tr title="${esc(p.local)}">
          <td>${esc(detalheLocal(p))}${notaTipico(p)}</td>
          <td><div class="mini-bar"><i style="width:${(p.previstoMin / max) * 100}%;background:${corNivel[p.nivel]}"></i></div></td>
          <td class="r num">${p.esperaAtualMin} <span class="muted">→</span> <b style="color:${corNivel[p.nivel]}">${p.previstoMin}</b></td>
          <td>${tendencia(p.tendenciaMin)}</td><td class="r">${chipProb(p, m.limiarLongaMin)}</td></tr>`).join('')}</tbody></table></div></div>`;
    }).join('')}</div>

    ${m.modeloAtivo && met.f1 != null ? `<h2 class="section-title">Qualidade do modelo <small>conjunto de teste · treino: ${esc(m.fonteTreino || '—')} · gerado em ${esc(m.geradoEm || '—')}</small></h2>
      <div class="panel"><div class="stats" style="border-bottom:0;grid-template-columns:repeat(auto-fit,minmax(150px,1fr))">
        <div class="stat"><b>${fmtDec(+met.mae_min.toFixed(1))} min</b><span>Erro médio</span></div>
        <div class="stat"><b>${fmtDec(+met.mae_baseline_min.toFixed(1))} min</b><span>Erro de "igual a agora"</span></div>
        <div class="stat"><b>${fmtDec(+met.r2.toFixed(2))}</b><span>R² (regressão)</span></div>
        <div class="stat"><b>${fmtDec(+met.recall.toFixed(2))}</b><span>Recall (longa)</span></div>
        <div class="stat"><b>${fmtDec(+met.f1.toFixed(2))}</b><span>F1 (longa)</span></div>
        <div class="stat"><b>${fmtDec(+met.auc.toFixed(2))}</b><span>ROC AUC</span></div></div></div>
      <p class="hint" style="padding-inline:2px">Os dados são médias agregadas por setor, não a experiência individual de cada pessoa.</p>` : ''}
    ${m.perfilAtivo && m.perfilMetricas && m.perfilMetricas.f1 != null
      ? blocoMetricas('Espera típica do horário', `modelo treinado com dados reais · ${esc(m.perfilFonte || '')} · baseline: mediana do setor`, m.perfilMetricas) : ''}`;
  icones();
}

// ------------------------------------------------------------------ MÉTRICAS (Chart.js)
const CH = { hora: null, local: null, dist: null };

function estiloGrafico() {
  const texto = cssVar('--muted'), linha = cssVar('--line');
  Chart.defaults.font.family = cssVar('--font');
  Chart.defaults.font.size = 12;
  Chart.defaults.color = texto;
  return { texto, linha };
}

function destruirGraficos() {
  Object.keys(CH).forEach((k) => { if (CH[k]) { CH[k].destroy(); CH[k] = null; } });
  S.metricasPronto = false;
}

function montarMetricas() {
  $('#metricasBody').innerHTML = `
    <div class="kpis" id="kpis"></div>
    <div class="charts">
      <div class="panel">
        <div class="panel-head"><h2>Espera média por horário</h2><select class="input select" id="metEstab" style="min-width:200px" aria-label="Unidade"></select></div>
        <div class="panel-body"><div class="chart-box"><canvas id="chHora"></canvas></div><div class="insights" id="insights"></div></div>
      </div>
      <div class="panel">
        <div class="panel-head"><h2>Espera média por unidade</h2></div>
        <div class="panel-body"><div class="chart-box"><canvas id="chLocal"></canvas></div></div>
      </div>
    </div>
    <div class="charts charts-2">
      <div class="panel">
        <div class="panel-head"><h2>Localização: distância média dos clientes</h2></div>
        <div class="panel-body"><div class="chart-box"><canvas id="chDist"></canvas></div></div>
      </div>
      <div class="panel">
        <div class="panel-head"><h2>Resumo por unidade</h2></div>
        <div class="table-wrap"><table class="tbl"><thead><tr><th>Unidade</th><th class="r">Espera</th><th class="r">Atend.</th><th class="r">Na fila</th></tr></thead><tbody id="resumoTab"></tbody></table></div>
      </div>
    </div>`;
  S.metricasPronto = true;
}

function barras(canvasId, chave, tipo, labels, dados, cores, horizontal) {
  const { texto, linha } = estiloGrafico();
  if (CH[chave]) {
    CH[chave].data.labels = labels; CH[chave].data.datasets[0].data = dados; CH[chave].data.datasets[0].backgroundColor = cores;
    CH[chave].update('none');
    return;
  }
  CH[chave] = new Chart($(canvasId), {
    type: tipo,
    data: { labels, datasets: [{ data: dados, backgroundColor: cores, borderRadius: 3, maxBarThickness: 36 }] },
    options: {
      indexAxis: horizontal ? 'y' : 'x', responsive: true, maintainAspectRatio: false, animation: false,
      plugins: { legend: { display: false }, tooltip: { callbacks: { label: (c) => ` ${fmtDec(c.parsed[horizontal ? 'x' : 'y'])} ${chave === 'dist' ? 'km' : 'min'}` } } },
      scales: {
        x: { grid: { display: !horizontal ? false : true, color: linha }, ticks: { color: texto } },
        y: { grid: { display: horizontal ? false : true, color: linha }, ticks: { color: texto }, beginAtZero: true },
      },
    },
  });
}

function renderMetricas(m) {
  if (!S.metricasPronto) montarMetricas();
  const ok = cssVar('--ok'), warn = cssVar('--warn'), bad = cssVar('--bad'), pri = cssVar('--primary'), info = cssVar('--info');

  $('#kpis').innerHTML = `
    <div class="kpi"><span class="eyebrow">Atendimentos</span><b>${m.atendimentosTotal.toLocaleString('pt-BR')}</b><p>Base histórica e ao vivo</p></div>
    <div class="kpi"><span class="eyebrow">Espera média</span><b>${fmtDec(m.esperaMediaMin)}<small>min</small></b><p>Entre entrar na fila e ser chamado</p></div>
    <div class="kpi"><span class="eyebrow">Distância média</span><b>${fmtDec(m.distanciaMediaClientesKm)}<small>km</small></b><p>De onde o cliente entra na fila</p></div>
    <div class="kpi"><span class="eyebrow">Na fila agora</span><b>${m.pessoasEmFilaAgora}</b><p>${m.ticketsAtivos} tickets ativos</p></div>`;

  const sel = $('#metEstab');
  const opcoes = '<option value="">Todas as unidades</option>' + m.estabelecimentos.slice().sort((a, b) => a.nome.localeCompare(b.nome))
    .map((e) => `<option value="${e.id}">${esc(e.nome)}</option>`).join('');
  if (sel.dataset.n !== String(m.estabelecimentos.length)) { sel.innerHTML = opcoes; sel.dataset.n = String(m.estabelecimentos.length); }
  sel.value = S.metricasEstab;

  const cor = (h) => (h.hora === m.melhorHorario ? ok : h.hora === m.piorHorario ? bad : pri);
  barras('#chHora', 'hora', 'bar', m.esperaPorHora.map((h) => `${h.hora}h`), m.esperaPorHora.map((h) => h.esperaMediaMin), m.esperaPorHora.map(cor), false);
  $('#insights').innerHTML = `
    ${m.melhorHorario != null ? `<span class="badge ok">Melhor horário: ${m.melhorHorario}h</span>` : ''}
    ${m.piorHorario != null ? `<span class="badge bad">Evite: ${m.piorHorario}h</span>` : ''}
    <span class="badge">Previsor: ${esc(m.previsor)}</span>`;

  const maxE = Math.max(...m.estabelecimentos.map((e) => e.esperaMediaMin), 1);
  const porEspera = m.estabelecimentos.slice().sort((a, b) => b.esperaMediaMin - a.esperaMediaMin);
  barras('#chLocal', 'local', 'bar', porEspera.map((e) => e.nome), porEspera.map((e) => e.esperaMediaMin),
    porEspera.map((e) => (e.esperaMediaMin > maxE * 0.66 ? bad : e.esperaMediaMin > maxE * 0.33 ? warn : ok)), true);
  const porDist = m.estabelecimentos.slice().sort((a, b) => b.distanciaMediaClientesKm - a.distanciaMediaClientesKm);
  barras('#chDist', 'dist', 'bar', porDist.map((e) => e.nome), porDist.map((e) => e.distanciaMediaClientesKm), porDist.map(() => info), true);

  $('#resumoTab').innerHTML = m.estabelecimentos.map((e) => `<tr><td>${esc(e.nome)}<small>${esc(e.bairro)} · serviço ${fmtDec(e.tempoServicoMedioMin)} min</small></td>
    <td class="r num">${fmtDec(e.esperaMediaMin)} min</td><td class="r num">${e.atendimentos.toLocaleString('pt-BR')}</td><td class="r num">${e.filaAgora}</td></tr>`).join('');
}
