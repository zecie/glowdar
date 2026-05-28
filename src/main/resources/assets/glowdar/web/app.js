(function () {
  'use strict';

  const ICON_PLUS  = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M12 5v14"/><path d="M5 12h14"/></svg>';
  const ICON_CHECK = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12l5 5 9-11"/></svg>';
  const ICON_X     = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M6 6l12 12"/><path d="M18 6L6 18"/></svg>';

  const state = {
    config: null,
    registries: null,
    activeTab: 'glow',
  };

  function send(op, payload) {
    console.log('!GLOWDAR!|' + op + '|' + JSON.stringify(payload || {}));
    flashStatus();
  }

  function flashStatus() {
    const el = document.getElementById('footSaved');
    if (!el) return;
    el.classList.remove('pulse');
    void el.offsetWidth;
    el.classList.add('pulse');
    el.textContent = 'synced';
  }

  function set(key, value) {
    if (!state.config) return;
    state.config[key] = value;
    send('set', { key: key, value: value });
  }

  function intToHex(n) {
    const h = (n & 0xFFFFFF).toString(16).padStart(6, '0');
    return '#' + h;
  }
  function hexToInt(h) {
    return parseInt(h.replace('#', ''), 16);
  }

  function setTab(name) {
    state.activeTab = name;
    document.querySelectorAll('.nav-tab').forEach(t => {
      t.classList.toggle('active', t.dataset.tab === name);
    });
    document.querySelectorAll('.pane').forEach(p => {
      p.classList.toggle('active', p.dataset.pane === name);
    });
    const map = {
      glow:     ['Glow',         'entity highlighting'],
      outlines: ['Outlines',     'block outlines'],
      perf:     ['Performance',  'cost vs. quality'],
      settings: ['Settings',     'panel + keybinds'],
    };
    if (map[name]) {
      document.getElementById('mainTitle').textContent = map[name][0];
      document.getElementById('mainSub').textContent   = map[name][1];
    }
    requestAnimationFrame(moveTabIndicator);
  }

  function moveTabIndicator() {
    const active = document.querySelector('.nav-tab.active');
    const indicator = document.getElementById('navIndicator');
    if (!active || !indicator) return;
    indicator.style.top    = active.offsetTop + 'px';
    indicator.style.height = active.offsetHeight + 'px';
    indicator.style.opacity = '1';
  }

  function setText(id, text) {
    const el = document.getElementById(id);
    if (el) el.textContent = text;
  }

  function updateRangeVisual(el) {
    const min = parseFloat(el.min);
    const max = parseFloat(el.max);
    const val = parseFloat(el.value);
    const p = ((val - min) / (max - min)) * 100;
    el.style.setProperty('--p', p + '%');
  }

  function syncInputs() {
    if (!state.config) return;
    document.querySelectorAll('[data-bind]').forEach(el => {
      const key = el.dataset.bind;
      const v = state.config[key];
      if (v === undefined) return;
      if (el.type === 'checkbox') {
        if (el.checked !== !!v) el.checked = !!v;
      } else if (el.type === 'color') {
        const hex = intToHex(v);
        if (el.value !== hex) el.value = hex;
      } else if (el.type === 'range') {
        if (parseFloat(el.value) !== parseFloat(v)) el.value = v;
        updateRangeVisual(el);
      }
    });

    setText('lwVal', (state.config.outlineLineWidth || 0).toFixed(1));
    setText('opVal', (state.config.outlineOpacity   || 0).toFixed(2));
    setText('grVal', String(Math.round(state.config.glowRange || 0)));
    setText('mxVal', String(state.config.maxGlowEntities || 0));
    setText('srVal', String(state.config.chunkScanRate || 0));
    setText('riVal', String(state.config.chunkRefreshInterval || 0));
    setText('goVal', (state.config.guiOpacity || 0).toFixed(2));

    const anim = state.config.espAnimMode;
    if (anim != null) {
      document.querySelectorAll('.seg-anim').forEach(btn => {
        btn.classList.toggle('on', btn.dataset.anim === anim);
      });
    }

    const opacity = state.config.guiOpacity;
    if (opacity != null) {
      const app = document.getElementById('app');
      if (app) app.style.opacity = String(opacity);
    }

    if (state.config.guiAccentColor != null) {
      const r = (state.config.guiAccentColor >> 16) & 0xFF;
      const g = (state.config.guiAccentColor >>  8) & 0xFF;
      const b =  state.config.guiAccentColor        & 0xFF;
      const c = intToHex(state.config.guiAccentColor);
      const root = document.documentElement;
      root.style.setProperty('--accent',        c);
      root.style.setProperty('--accent-bright', '#' + Math.min(255, r + 38).toString(16).padStart(2,'0') + Math.min(255, g + 38).toString(16).padStart(2,'0') + Math.min(255, b + 38).toString(16).padStart(2,'0'));
      root.style.setProperty('--accent-deep',   '#' + Math.max(0, r - 32).toString(16).padStart(2,'0') + Math.max(0, g - 32).toString(16).padStart(2,'0') + Math.max(0, b - 32).toString(16).padStart(2,'0'));
      root.style.setProperty('--accent-glow',   `rgba(${r},${g},${b},0.28)`);
      root.style.setProperty('--accent-fade',   `rgba(${r},${g},${b},0.09)`);
    }
  }

  function bindAllInputs() {
    document.querySelectorAll('[data-bind]').forEach(el => {
      const key = el.dataset.bind;
      el.addEventListener('input', () => {
        if (el.type === 'checkbox') set(key, el.checked);
        else if (el.type === 'color') set(key, hexToInt(el.value));
        else if (el.type === 'range') {
          const v = (el.step && el.step.indexOf('.') >= 0) ? parseFloat(el.value) : parseInt(el.value, 10);
          set(key, v);
          updateRangeVisual(el);
          syncInputs();
        }
      });
    });
  }

  function prettyId(id) {
    const i = id.indexOf(':');
    if (i < 0) return ['', id];
    return [id.substring(0, i) + ':', id.substring(i + 1)];
  }

  function renderBlockList(query) {
    const list = document.getElementById('blockList');
    const counter = document.getElementById('blockCounter');
    if (!state.registries) { list.innerHTML = ''; if (counter) counter.textContent = ''; return; }
    const q = (query || '').toLowerCase();
    const inWhitelist = new Set((state.config.blockWhitelist || []).map(b => b.id));
    const all = state.registries.blocks.filter(id => !q || id.toLowerCase().includes(q));
    const limit = 100;
    const matched = all.slice(0, limit);
    list.innerHTML = '';
    matched.forEach(id => {
      const row = document.createElement('div');
      row.className = 'picker-item';
      const [ns, body] = prettyId(id);
      row.innerHTML = '<div class="id"><span class="ns">' + ns + '</span>' + body + '</div>';
      const btn = document.createElement('button');
      const on = inWhitelist.has(id);
      btn.className = 'btn-pill' + (on ? ' on' : '');
      btn.innerHTML = (on ? ICON_CHECK + 'added' : ICON_PLUS + 'add');
      btn.onclick = () => {
        if (on) send('blockRemove', { id: id });
        else    send('blockAdd',    { id: id });
      };
      row.appendChild(btn);
      list.appendChild(row);
    });
    if (counter) {
      counter.textContent = all.length > limit ? matched.length + '/' + all.length : String(all.length);
    }
  }

  function renderActiveBlocks() {
    const host = document.getElementById('blockActive');
    const countEl = document.getElementById('blockActiveCount');
    host.innerHTML = '';
    const list = state.config.blockWhitelist || [];
    if (countEl) countEl.textContent = String(list.length);
    list.forEach(entry => {
      const card = document.createElement('div');
      card.className = 'block-card';

      const swatch = document.createElement('label');
      swatch.className = 'color-swatch';
      swatch.style.flexShrink = '0';
      const color = document.createElement('input');
      color.type = 'color';
      color.value = intToHex(entry.color);
      color.oninput = () => send('blockColor', { id: entry.id, color: hexToInt(color.value) });
      swatch.appendChild(color);

      const [ns, body] = prettyId(entry.id);
      const idDiv = document.createElement('div');
      idDiv.className = 'id';
      idDiv.title = entry.id;
      idDiv.innerHTML = '<span class="ns">' + ns + '</span>' + body;

      const rm = document.createElement('button');
      rm.className = 'btn-ico';
      rm.title = 'remove';
      rm.innerHTML = ICON_X;
      rm.onclick = () => send('blockRemove', { id: entry.id });

      card.appendChild(swatch);
      card.appendChild(idDiv);
      card.appendChild(rm);
      host.appendChild(card);
    });
  }

  function renderMobList(query) {
    const list = document.getElementById('mobList');
    const counter = document.getElementById('mobCounter');
    if (!state.registries) { list.innerHTML = ''; if (counter) counter.textContent = ''; return; }
    const q = (query || '').toLowerCase();
    const inWhitelist = new Set(state.config.mobGlowWhitelist || []);
    const all = state.registries.mobs.filter(id => !q || id.toLowerCase().includes(q));
    const limit = 100;
    const matched = all.slice(0, limit);
    list.innerHTML = '';
    matched.forEach(id => {
      const row = document.createElement('div');
      row.className = 'picker-item';
      const [ns, body] = prettyId(id);
      row.innerHTML = '<div class="id"><span class="ns">' + ns + '</span>' + body + '</div>';
      const btn = document.createElement('button');
      const on = inWhitelist.has(id);
      btn.className = 'btn-pill' + (on ? ' on' : '');
      btn.innerHTML = (on ? ICON_CHECK + 'added' : ICON_PLUS + 'add');
      btn.onclick = () => {
        if (on) send('mobRemove', { id: id });
        else    send('mobAdd',    { id: id });
      };
      row.appendChild(btn);
      list.appendChild(row);
    });
    if (counter) {
      counter.textContent = all.length > limit ? matched.length + '/' + all.length : String(all.length);
    }
  }

  function renderActiveMobs() {
    const host = document.getElementById('mobActive');
    const countEl = document.getElementById('mobActiveCount');
    host.innerHTML = '';
    const list = state.config.mobGlowWhitelist || [];
    if (countEl) countEl.textContent = String(list.length);
    list.forEach(id => {
      const chip = document.createElement('div');
      chip.className = 'chip';
      const [ns, body] = prettyId(id);
      chip.innerHTML = '<span><span class="ns">' + ns + '</span>' + body + '</span>';
      const x = document.createElement('button');
      x.innerHTML = ICON_X;
      x.onclick = () => send('mobRemove', { id: id });
      chip.appendChild(x);
      host.appendChild(chip);
    });
  }

  function refreshStatus() {
    const blocks = (state.config && state.config.blockWhitelist) ? state.config.blockWhitelist.length : 0;
    const mobs   = (state.config && state.config.mobGlowWhitelist) ? state.config.mobGlowWhitelist.length : 0;
    setText('mainStatus', blocks + ' blocks · ' + mobs + ' mobs');
    setText('footStatus', 'glowdar');
  }

  function onState(payload) {
    if (!payload) return;
    state.config     = payload.config     || state.config;
    state.registries = payload.registries || state.registries;
    syncInputs();
    const bs = document.getElementById('blockSearch');
    const ms = document.getElementById('mobSearch');
    renderBlockList(bs ? bs.value : '');
    renderActiveBlocks();
    renderMobList(ms ? ms.value : '');
    renderActiveMobs();
    refreshStatus();
    setText('footSaved', 'synced');
  }

  window.glowdar = window.glowdar || {};
  window.glowdar.onStateUpdate = onState;

  function init() {
    document.querySelectorAll('.nav-tab').forEach(t => {
      t.addEventListener('click', () => setTab(t.dataset.tab));
    });
    document.getElementById('closeBtn').addEventListener('click', () => send('close', {}));
    document.getElementById('blockSearch').addEventListener('input', e => renderBlockList(e.target.value));
    document.getElementById('mobSearch').addEventListener('input', e => renderMobList(e.target.value));

    bindAllInputs();

    document.querySelectorAll('.seg-anim').forEach(btn => {
      btn.addEventListener('click', () => {
        set('espAnimMode', btn.dataset.anim);
        syncInputs();
      });
    });

    setTab('glow');

    if (window.glowdar && window.glowdar.state) onState(window.glowdar.state);
    send('refresh', {});

    window.addEventListener('resize', moveTabIndicator);
  }

  let _rafTick = 0;
  function rafLoop() {
    _rafTick = (_rafTick + 1) & 0xFFFF;
    document.documentElement.style.setProperty('--raf', _rafTick);
    requestAnimationFrame(rafLoop);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => { init(); requestAnimationFrame(rafLoop); });
  } else {
    init();
    requestAnimationFrame(rafLoop);
  }
})();
