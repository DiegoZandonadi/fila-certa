/* Widget de suporte. Fala com POST /api/bot/mensagem (que usa o bot externo se configurado). */
const BOT = {
  sessao: store.get('sessao', null) || (() => { const s = (crypto.randomUUID ? crypto.randomUUID() : String(Date.now())); store.set('sessao', s); return s; })(),
  aberto: false,
  iniciou: false,
};
const SUGESTOES_PADRAO = ['Quanto tempo falta?', 'Quando devo sair?', 'Qual a fila mais rápida?', 'Como funciona?'];

function botAdd(texto, quem, origem) {
  const box = $('#msgs');
  const el = document.createElement('div');
  el.className = `msg from-${quem}`;
  el.innerHTML = `${esc(texto)}${origem ? `<small>${esc(origem)}</small>` : ''}`;
  box.appendChild(el);
  box.scrollTop = box.scrollHeight;
  return el;
}

function botSugestoes(lista) {
  $('#quick').innerHTML = (lista && lista.length ? lista : SUGESTOES_PADRAO)
    .map((s) => `<button type="button" data-q="${esc(s)}">${esc(s)}</button>`).join('');
}

function botAbrir(abrir) {
  BOT.aberto = abrir;
  $('#bot').classList.toggle('hidden', !abrir);
  $('#botFab').classList.toggle('hidden', abrir);
  if (abrir) {
    if (!BOT.iniciou) {
      BOT.iniciou = true;
      botAdd('Olá. Sou o suporte da Fila Certa. Posso informar quanto falta, quando você deve sair e qual a fila mais rápida perto de você.', 'bot');
      botSugestoes();
    }
    $('#botInput').focus();
  }
}

async function botEnviar(texto) {
  texto = texto.trim();
  if (!texto) return;
  botAdd(texto, 'me');
  $('#botInput').value = '';
  const digitando = document.createElement('div');
  digitando.className = 'msg from-bot';
  digitando.innerHTML = '<span class="typing"><i></i><i></i><i></i></span>';
  $('#msgs').appendChild(digitando);
  $('#msgs').scrollTop = 1e6;
  try {
    const r = await api('/bot/mensagem', {
      method: 'POST',
      body: { mensagem: texto, sessaoId: BOT.sessao, ticketId: S.ticketId, latitude: S.loc.lat, longitude: S.loc.lng, modo: S.modo },
    });
    await new Promise((ok) => setTimeout(ok, 300));
    digitando.remove();
    botAdd(r.resposta, 'bot', r.origem === 'bot-externo' ? 'Agente de suporte' : 'Assistente local');
    $('#botOrigem').textContent = r.origem === 'bot-externo' ? 'Agente conectado' : 'Modo local';
    botSugestoes(r.sugestoes);
  } catch (err) {
    digitando.remove();
    botAdd('Não consegui responder agora. Tente novamente em instantes.', 'bot');
  }
}

function iniciarBot() {
  $('#botFab').onclick = () => botAbrir(true);
  $('#botClose').onclick = () => botAbrir(false);
  $('#botForm').onsubmit = (ev) => { ev.preventDefault(); botEnviar($('#botInput').value); };
  $('#quick').addEventListener('click', (ev) => { const b = ev.target.closest('[data-q]'); if (b) botEnviar(b.dataset.q); });
}
