/* Utilidades compartilhadas: acesso à API, estado global, toasts e sons. */
const $ = (sel, el = document) => el.querySelector(sel);
const $$ = (sel, el = document) => [...el.querySelectorAll(sel)];
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

/** Ícone Lucide: devolve o marcador; chame icones() depois de inserir no DOM. */
const ic = (nome, cls = '') => `<i data-lucide="${nome}"${cls ? ` class="${cls}"` : ''}></i>`;
const icones = () => { if (window.lucide) window.lucide.createIcons(); };

/** Chamada à API com tempo limite: uma requisição travada nunca deixa a tela "presa". */
async function api(path, opts = {}) {
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), opts.timeout || 12000);
  try {
    const res = await fetch('/api' + path, {
      method: opts.method || 'GET',
      headers: { 'Content-Type': 'application/json' },
      body: opts.body ? JSON.stringify(opts.body) : undefined,
      signal: ctl.signal,
    });
    let data = null;
    try { data = await res.json(); } catch (_) { /* sem corpo */ }
    if (!res.ok) throw new Error((data && data.mensagem) || `Erro ${res.status}`);
    return data;
  } catch (err) {
    if (err.name === 'AbortError') throw new Error('O servidor demorou a responder. Tente novamente.');
    throw err;
  } finally {
    clearTimeout(timer);
  }
}

const store = {
  get(k, d) { try { const v = localStorage.getItem('fc.' + k); return v === null ? d : JSON.parse(v); } catch (_) { return d; } },
  set(k, v) { try { localStorage.setItem('fc.' + k, JSON.stringify(v)); } catch (_) { /* ignora */ } },
  del(k) { try { localStorage.removeItem('fc.' + k); } catch (_) { /* ignora */ } },
};

const CAT_ICON = { SAUDE: 'hospital', BANCO: 'landmark', GOVERNO: 'building-2', CARTORIO: 'stamp', RESTAURANTE: 'utensils' };
const MODOS = { A_PE: { icon: 'footprints', nome: 'A pé' }, TRANSPORTE: { icon: 'bus', nome: 'Transporte' }, CARRO: { icon: 'car', nome: 'Carro' } };
const NIVEL = { VERDE: 'ok', AMARELO: 'warn', VERMELHO: 'bad' };
const LOCAL_PADRAO = { lat: -23.5113, lng: -46.8760 }; // centro de Barueri (até as unidades carregarem)

/** Estado global (única fonte de verdade das telas). */
const S = {
  view: 'descobrir',
  tema: 'light',
  loc: store.get('loc', LOCAL_PADRAO),
  locFonte: store.get('locFonte', 'padrao'),
  locRotulo: store.get('locRotulo', ''),
  zonaAtiva: null,
  modo: store.get('modo', 'TRANSPORTE'),
  categoria: null,
  catsDisponiveis: null,
  centralizou: false,
  busca: '',
  lista: [],
  selecionado: null,
  ticketId: store.get('ticketId', null),
  ticket: null,
  espera0: store.get('espera0', 0),
  painelId: store.get('painelId', null),
  metricasEstab: '',
  fator: 1,
  assinaturas: {}, // evita redesenhar quando nada mudou
};

/** true se o conteúdo mudou desde a última vez (por chave). */
function mudou(chave, valor) {
  const s = JSON.stringify(valor);
  if (S.assinaturas[chave] === s) return false;
  S.assinaturas[chave] = s;
  return true;
}

function fmtMin(m) {
  if (m == null) return '—';
  if (m < 60) return `${m} min`;
  return `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min`;
}
function fmtKm(km) { return km == null ? '—' : `${String(km.toFixed(1)).replace('.', ',')} km`; }
function fmtDec(n) { return String(n).replace('.', ','); }

function toast(msg, tipo = '', icone = null) {
  const nome = icone || ({ ok: 'circle-check', err: 'triangle-alert', alert: 'bell-ring' }[tipo] || 'info');
  const el = document.createElement('div');
  el.className = `toast ${tipo}`;
  el.innerHTML = `${ic(nome)}<span>${esc(msg)}</span>`;
  $('#toasts').appendChild(el);
  icones();
  setTimeout(() => { el.style.opacity = '0'; el.style.transition = 'opacity .3s'; setTimeout(() => el.remove(), 300); }, 4500);
}

let audioCtx = null;
function beep(freqs = [880, 1175]) {
  try {
    audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
    freqs.forEach((f, i) => {
      const o = audioCtx.createOscillator();
      const g = audioCtx.createGain();
      o.frequency.value = f; o.type = 'sine';
      g.gain.setValueAtTime(0.0001, audioCtx.currentTime + i * 0.16);
      g.gain.exponentialRampToValueAtTime(0.15, audioCtx.currentTime + i * 0.16 + 0.02);
      g.gain.exponentialRampToValueAtTime(0.0001, audioCtx.currentTime + i * 0.16 + 0.15);
      o.connect(g); g.connect(audioCtx.destination);
      o.start(audioCtx.currentTime + i * 0.16); o.stop(audioCtx.currentTime + i * 0.16 + 0.16);
    });
    if (navigator.vibrate) navigator.vibrate([120, 60, 120]);
  } catch (_) { /* áudio bloqueado: tudo bem */ }
}

/** Minúsculas e sem acento, para busca tolerante ("paraiso" encontra "Paraíso"). */
const norm = (s) => String(s ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

/** Unidades que passam na busca (todas as palavras digitadas precisam aparecer). */
function visiveis() {
  const termos = norm(S.busca).split(/\s+/).filter(Boolean);
  if (!termos.length) return S.lista;
  return S.lista.filter((e) => {
    const alvo = norm(`${e.nome} ${e.bairro} ${e.endereco} ${e.categoriaRotulo}`);
    return termos.every((t) => alvo.includes(t));
  });
}

const cssVar = (nome) => getComputedStyle(document.documentElement).getPropertyValue(nome).trim();
