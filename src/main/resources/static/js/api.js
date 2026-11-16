/* Utilidades compartilhadas: acesso a API, estado global, toasts e sons. */
const $ = (sel, el = document) => el.querySelector(sel);
const $$ = (sel, el = document) => [...el.querySelectorAll(sel)];
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

async function api(path, opts = {}) {
  const res = await fetch('/api' + path, {
    method: opts.method || 'GET',
    headers: { 'Content-Type': 'application/json' },
    body: opts.body ? JSON.stringify(opts.body) : undefined,
  });
  let data = null;
  try { data = await res.json(); } catch (_) { /* sem corpo */ }
  if (!res.ok) throw new Error((data && data.mensagem) || `Erro ${res.status}`);
  return data;
}

const store = {
  get(k, d) { try { const v = localStorage.getItem('fc.' + k); return v === null ? d : JSON.parse(v); } catch (_) { return d; } },
  set(k, v) { try { localStorage.setItem('fc.' + k, JSON.stringify(v)); } catch (_) { /* ignora */ } },
  del(k) { try { localStorage.removeItem('fc.' + k); } catch (_) { /* ignora */ } },
};

const CAT = { SAUDE: '🏥', BANCO: '🏦', GOVERNO: '🏛️', CARTORIO: '📜', RESTAURANTE: '🍽️' };
const MODOS = { A_PE: { icon: '🚶', nome: 'A pé' }, TRANSPORTE: { icon: '🚌', nome: 'Transporte' }, CARRO: { icon: '🚗', nome: 'Carro' } };
const NIVEL_COR = { VERDE: 'var(--ok)', AMARELO: 'var(--warn)', VERMELHO: 'var(--bad)' };
const LOCAL_PADRAO = { lat: -23.5614, lng: -46.6559 }; // Av. Paulista

/** Estado global (uma unica fonte de verdade para as telas). */
const S = {
  view: 'descobrir',
  loc: store.get('loc', LOCAL_PADRAO),
  locFonte: store.get('locFonte', 'padrao'),
  modo: store.get('modo', 'TRANSPORTE'),
  categoria: null,
  lista: [],
  selecionado: null,
  ticketId: store.get('ticketId', null),
  ticket: null,
  espera0: store.get('espera0', 0),
  painelId: store.get('painelId', null),
  metricasEstab: '',
  arrastando: false,
  fator: 1,
};

function fmtMin(m) {
  if (m == null) return '—';
  if (m < 60) return `${m} min`;
  return `${Math.floor(m / 60)}h${String(m % 60).padStart(2, '0')}`;
}
function fmtKm(km) { return km == null ? '—' : `${String(km.toFixed(1)).replace('.', ',')} km`; }
function fmtDec(n) { return String(n).replace('.', ','); }

function toast(msg, tipo = '', icone = '') {
  const el = document.createElement('div');
  el.className = `toast ${tipo}`;
  el.innerHTML = `<span>${icone}</span><span>${esc(msg)}</span>`;
  $('#toasts').appendChild(el);
  setTimeout(() => { el.style.opacity = '0'; el.style.transition = 'opacity .4s'; setTimeout(() => el.remove(), 400); }, 4500);
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
      g.gain.exponentialRampToValueAtTime(0.18, audioCtx.currentTime + i * 0.16 + 0.02);
      g.gain.exponentialRampToValueAtTime(0.0001, audioCtx.currentTime + i * 0.16 + 0.15);
      o.connect(g); g.connect(audioCtx.destination);
      o.start(audioCtx.currentTime + i * 0.16); o.stop(audioCtx.currentTime + i * 0.16 + 0.16);
    });
    if (navigator.vibrate) navigator.vibrate([120, 60, 120]);
  } catch (_) { /* audio bloqueado: tudo bem */ }
}
