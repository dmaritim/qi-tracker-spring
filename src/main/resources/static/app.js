/* ============================================================
   STATE
   ============================================================ */
let currentUser = null;
let projects = [];
let currentProjectId = null;
let currentView = 'dashboard'; // 'dashboard' | 'manage'
let dashboardData = null;
let indicators = [];
let processAreas = [];
let entriesByIndicator = {};
let members = [];
let pdsaCycles = [];

let editingProjectId = null;
let editingAreaId = null;
let editingIndicatorId = null;
let editingEntryId = null;
let editingPdsaId = null;
let activeIndicatorForEntry = null;
let tempNumerator = [];
let tempDenominator = [];

const FREQ_LABEL = { daily: 'Daily', weekly: 'Weekly', biweekly: 'Biweekly', monthly: 'Monthly', quarterly: 'Quarterly' };

/* ============================================================
   API CLIENT
   ============================================================ */
async function api(path, options = {}) {
  const res = await fetch(path, {
    credentials: 'include',
    headers: options.body ? { 'Content-Type': 'application/json' } : {},
    ...options
  });
  if (res.status === 401) { window.location.href = '/login.html'; throw new Error('Not signed in'); }
  let data = null;
  try { data = await res.json(); } catch (e) { /* no body */ }
  if (!res.ok) throw new Error((data && data.error) || 'Something went wrong. Please try again.');
  return data;
}
async function logout() { await api('/api/auth/logout', { method: 'POST' }); window.location.href = '/login.html'; }

function escapeHtml(s) {
  if (s === undefined || s === null) return '';
  return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function fmtDate(d) { if (!d) return '—'; const dt = new Date(d); return dt.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }); }
function round2(n) { return Math.round((n + Number.EPSILON) * 100) / 100; }

/* ============================================================
   INIT
   ============================================================ */
async function init() {
  document.getElementById('mainContent').innerHTML = `<div class="faint" style="padding:40px 0;">Loading…</div>`;
  try {
    const me = await api('/api/auth/me');
    currentUser = me.user;
    renderUserRow();
    await loadProjectList();
    renderProjectListView();
  } catch (err) {
    if (err.message !== 'Not signed in') {
      document.getElementById('mainContent').innerHTML = `<div class="empty-state"><h3>Couldn't load your projects</h3><p>${escapeHtml(err.message)}</p><button class="btn-primary" onclick="init()">Try again</button></div>`;
    }
  }
}
init();

function renderUserRow() {
  const row = document.getElementById('userRow');
  if (!currentUser) { row.innerHTML = ''; return; }
  const initials = currentUser.name.trim().split(/\s+/).map(w => w[0]).slice(0, 2).join('').toUpperCase();
  row.innerHTML = `
    <div class="who"><div class="avatar">${initials}</div><div class="name">${escapeHtml(currentUser.name)}</div></div>
    <button class="signout" onclick="logout()">Sign out</button>`;
}

async function loadProjectList() {
  projects = await api('/api/projects');
}

/* ============================================================
   SIDEBAR
   ============================================================ */
function renderSidebar() {
  const list = document.getElementById('sidebarList');
  if (projects.length === 0) {
    list.innerHTML = `<div class="sidebar-label">Projects</div><div style="padding:8px 10px;font-size:12.5px;color:var(--ink-faint);">No projects yet.</div>`;
    return;
  }
  list.innerHTML = `<div class="sidebar-label">Projects</div>` + projects.map(p => `
    <button class="project-tab ${p.id === currentProjectId ? 'active' : ''}" onclick="openProject(${p.id})">
      <span class="dot" style="background:${p.isCreator ? 'var(--teal)' : (p.isMember ? 'var(--steel)' : 'var(--ink-faint)')}"></span>
      <span class="label">${escapeHtml(p.name)}</span>
    </button>`).join('');
}

/* ============================================================
   PROJECT LIST VIEW
   ============================================================ */
function renderProjectListView() {
  currentProjectId = null;
  const el = document.getElementById('mainContent');
  if (projects.length === 0) {
    el.innerHTML = `
      <div class="dash-header"><div><h1>Quality improvement, tracked</h1><p>Run every QI project from one place — objectives, process areas, indicators, and progress against target.</p></div></div>
      <div class="empty-state"><h3>No projects yet</h3><p>Start your first QI project.</p><button class="btn-primary" onclick="openProjectModal()">Create your first project</button></div>`;
    renderSidebar();
    return;
  }
  el.innerHTML = `
    <div class="dash-header">
      <div><h1>Projects</h1><p>Everything your team is tracking. Open one for its live dashboard.</p></div>
      <button class="btn-primary" onclick="openProjectModal()">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M7 2V12M2 7H12" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
        New project
      </button>
    </div>
    <div class="project-grid">
      ${projects.map(p => {
        const s = p.summary || {};
        const statusMap = { ontrack: ['On track', 'ontrack'], duesoon: ['Due soon', 'duesoon'], overdue: ['Overdue', 'overdue'], nodata: ['No data yet', 'nodata'] };
        const [statusLabel, statusClass] = statusMap[s.projectStatus] || statusMap.nodata;
        const hasIndicators = (s.totalIndicators || 0) > 0;
        const hasPercent = s.overallPercentOnTarget !== null && s.overallPercentOnTarget !== undefined;
        return `<div class="project-card" onclick="openProject(${p.id})">
          <div class="top"><h3>${escapeHtml(p.name)}</h3>
            <span class="badge ${statusClass}">${statusLabel}</span>
          </div>
          <div class="meta-row">
            <span class="badge role" style="margin:0;">${p.isCreator ? 'Creator' : (p.isMember ? 'Member' : 'Viewer')}</span>
            <span>${FREQ_LABEL[p.reportingFrequency] || p.reportingFrequency} reporting</span>
          </div>
          <div class="card-performance">
            ${hasIndicators ? `
              <div class="perf-row">
                <span class="perf-stat">${s.totalIndicators} indicator${s.totalIndicators === 1 ? '' : 's'}</span>
                <span class="perf-stat on">${s.onTargetCount} on target</span>
                <span class="perf-stat off">${s.offTargetCount} off target</span>
              </div>
              ${hasPercent ? `<div class="perf-bar"><div class="perf-bar-fill" style="width:${s.overallPercentOnTarget}%;"></div></div>` : ''}
            ` : `<div class="faint" style="font-size:12px;">No indicators yet</div>`}
            <div class="perf-meta">${s.lastReportedDate ? 'Last reported ' + fmtDate(s.lastReportedDate) : 'No entries logged yet'}</div>
          </div>
        </div>`;
      }).join('')}
    </div>`;
  renderSidebar();
}

/* ============================================================
   OPEN A PROJECT -> default to its Dashboard view
   ============================================================ */
async function openProject(id) {
  currentProjectId = id;
  currentView = 'dashboard';
  renderSidebar();
  document.getElementById('mainContent').innerHTML = `<div class="faint" style="padding:40px 0;">Loading dashboard…</div>`;
  try {
    await loadDashboard();
    renderProjectShell();
  } catch (err) {
    document.getElementById('mainContent').innerHTML = `<div class="empty-state"><h3>Couldn't load this project</h3><p>${escapeHtml(err.message)}</p></div>`;
  }
  if (window.innerWidth <= 860) document.getElementById('sidebar').classList.remove('open');
}

async function loadDashboard() {
  dashboardData = await api(`/api/projects/${currentProjectId}/dashboard`);
}

async function switchView(view) {
  currentView = view;
  if (view === 'manage' && indicators.length === 0 && processAreas.length === 0) {
    await loadManageData();
  }
  renderProjectShell();
}

async function loadManageData() {
  const [indRes, areaRes, pdsaRes] = await Promise.all([
    api(`/api/projects/${currentProjectId}/indicators`),
    api(`/api/projects/${currentProjectId}/process-areas`),
    api(`/api/projects/${currentProjectId}/pdsa-cycles`)
  ]);
  indicators = indRes;
  processAreas = areaRes;
  pdsaCycles = pdsaRes;
  entriesByIndicator = {};
  await Promise.all(indicators.map(async ind => {
    entriesByIndicator[ind.id] = await api(`/api/indicators/${ind.id}/entries`);
  }));
}

/* ============================================================
   PROJECT SHELL (breadcrumb + header + tabs + active view)
   ============================================================ */
function renderProjectShell() {
  const p = dashboardData.project;
  const el = document.getElementById('mainContent');
  const canManage = p.isCreator || p.isMember;

  el.innerHTML = `
    <div class="breadcrumb" onclick="backToList()">
      <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><path d="M8 3L4 6.5L8 10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
      All projects
    </div>
    <div class="project-header">
      <div><h1>${escapeHtml(p.name)}<span class="badge role">${p.isCreator ? 'Creator' : (p.isMember ? 'Member' : 'Viewer')}</span></h1></div>
      <div class="actions">
        ${p.isCreator ? `<button class="btn-secondary" onclick="sendReportNow()">Send report now</button>` : ''}
        ${p.isCreator ? `<button class="btn-secondary" onclick="openMembersModal()">Members</button>` : ''}
        ${p.isCreator ? `<button class="btn-secondary" onclick="openProjectModal('${p.id}')">Edit project</button>` : ''}
        ${p.isCreator ? `<button class="btn-secondary" style="color:var(--brick);" onclick="confirmDeleteProject('${p.id}')">Delete</button>` : ''}
      </div>
    </div>
    ${canManage ? `
      <div class="view-tabs">
        <div class="view-tab ${currentView === 'dashboard' ? 'active' : ''}" onclick="switchView('dashboard')">Dashboard</div>
        <div class="view-tab ${currentView === 'manage' ? 'active' : ''}" onclick="switchView('manage')">Manage</div>
      </div>` : `<div style="margin-top:14px;"></div>`}
    <div id="viewContent"></div>
  `;
  if (currentView === 'manage' && canManage) renderManageView();
  else renderDashboardView();
}

function backToList() {
  currentProjectId = null;
  renderProjectListView();
}

/* ============================================================
   DASHBOARD VIEW — snapshot, stage, process-area trend groups
   ============================================================ */
function renderDashboardView() {
  const d = dashboardData;
  const container = document.getElementById('viewContent');
  const s = d.summary;
  const st = d.stage;

  const statusBadge = { ontrack: ['On track', 'ontrack'], duesoon: ['Due soon', 'duesoon'], overdue: ['Overdue', 'overdue'], nodata: ['No data yet', 'nodata'] }[s.projectStatus] || ['—', 'nodata'];

  let stageHtml = '';
  if (st.percentElapsed === null || st.percentElapsed === undefined) {
    stageHtml = `<div class="stage-box"><div class="muted" style="font-size:13px;">Set a start date and duration on this project to track its stage.</div></div>`;
  } else {
    const pct = Math.min(100, st.percentElapsed);
    stageHtml = `
      <div class="stage-box">
        <div class="stage-label-row"><span class="label">${escapeHtml(st.label)}</span><span class="end">Target end: ${fmtDate(st.endDate)}</span></div>
        <div class="stage-bar"><div class="stage-bar-fill ${st.label === 'Past end date' ? 'past' : ''}" style="width:${pct}%;"></div></div>
        <div class="stage-meta"><span>${st.daysElapsed} of ${st.daysTotal} days elapsed</span><span>${Math.round(st.percentElapsed)}%</span></div>
      </div>`;
  }

  container.innerHTML = `
    <div class="snapshot">
      <div class="snapshot-top">
        <div><div class="eyebrow">Snapshot</div><h2 style="font-size:18px;">Overall progress</h2></div>
        <span class="badge ${statusBadge[1]}">${statusBadge[0]}</span>
      </div>
      <div class="snapshot-stats">
        <div class="stat"><div class="num">${s.totalIndicators}</div><div class="lbl">Indicators</div></div>
        <div class="stat"><div class="num" style="color:var(--teal-dark);">${s.onTargetCount}</div><div class="lbl">On target</div></div>
        <div class="stat"><div class="num" style="color:var(--brick);">${s.offTargetCount}</div><div class="lbl">Off target</div></div>
        <div class="stat"><div class="num">${s.overallPercentOnTarget === null ? '—' : s.overallPercentOnTarget + '%'}</div><div class="lbl">On-target rate</div></div>
        <div class="stat"><div class="num mono" style="font-size:16px;">${fmtDate(s.lastReportedDate)}</div><div class="lbl">Last reported</div></div>
      </div>
      ${stageHtml}
    </div>
  `;

  const totalIndicators = d.groups.reduce((n, g) => n + g.indicators.length, 0) + d.ungrouped.length;
  if (totalIndicators === 0) {
    container.innerHTML += `<div class="empty-state"><h3>No indicators yet</h3><p>Once indicators are added under Manage, their trends will show up here.</p></div>`;
    return;
  }

  for (const group of d.groups) {
    container.innerHTML += `<div class="area-group"><div class="area-title"><h3>${escapeHtml(group.processAreaName)}</h3></div>${group.indicators.map(t => trendCard(t, d.pdsaCycles)).join('')}</div>`;
  }
  if (d.ungrouped.length) {
    container.innerHTML += `<div class="area-group"><div class="area-title"><h3>Ungrouped</h3></div>${d.ungrouped.map(t => trendCard(t, d.pdsaCycles)).join('')}</div>`;
  }
}

function trendCard(t, allMarkers) {
  const statusBadge = t.onTarget === null || t.onTarget === undefined ? '' :
    (t.onTarget ? `<span class="badge ontrack">On target</span>` : `<span class="badge overdue">Off target</span>`);
  const markers = (allMarkers || []).filter(m => m.indicatorIds && m.indicatorIds.includes(t.indicatorId));

  return `<div class="trend-card">
    <div class="trend-top">
      <h4>${escapeHtml(t.indicatorName)}</h4>
      <div style="display:flex;align-items:center;gap:10px;">
        <span class="mono" style="font-size:13px;">${t.latestValue === null || t.latestValue === undefined ? 'No data' : t.latestValue + (t.unit ? ' ' + escapeHtml(t.unit) : '')}</span>
        ${t.targetValue !== null && t.targetValue !== undefined ? `<span class="faint" style="font-size:12px;">target ${t.targetValue}</span>` : ''}
        ${statusBadge}
      </div>
    </div>
    <div class="trend-chart">${trendLineSvg(t, markers)}</div>
  </div>`;
}

function periodIndexForDate(periods, dateStr) {
  const target = new Date(dateStr).getTime();
  let idx = 0;
  for (let i = 0; i < periods.length; i++) {
    if (new Date(periods[i].periodStart).getTime() <= target) idx = i;
    else break;
  }
  return idx;
}

function trendLineSvg(t, markers) {
  const periods = t.periods;
  const present = periods.map((p, idx) => ({ ...p, idx })).filter(p => p.avgValue !== null && p.avgValue !== undefined);

  if (present.length === 0) {
    return `<div class="faint" style="font-size:12.5px;padding:14px 0;">No entries logged yet — the line will start as soon as the first one is.</div>`;
  }

  const w = 640, h = 140, padL = 40, padR = 14, padT = 22, padB = 24;
  const innerW = w - padL - padR, innerH = h - padT - padB;

  const values = present.map(p => p.avgValue);
  const hasTarget = t.targetValue !== null && t.targetValue !== undefined;
  let min = Math.min(...values, hasTarget ? t.targetValue : values[0]);
  let max = Math.max(...values, hasTarget ? t.targetValue : values[0]);
  if (min === max) { min -= 1; max += 1; }
  const pad = (max - min) * 0.12;
  min -= pad; max += pad;

  const xFor = idx => periods.length <= 1 ? padL + innerW / 2 : padL + (idx / (periods.length - 1)) * innerW;
  const yFor = v => padT + innerH - ((v - min) / (max - min)) * innerH;
  const targetY = hasTarget ? yFor(t.targetValue) : null;

  const linePath = present.map((p, i) => (i === 0 ? 'M' : 'L') + xFor(p.idx).toFixed(1) + ',' + yFor(p.avgValue).toFixed(1)).join(' ');

  const dots = present.map(p => {
    let onTarget = true;
    if (hasTarget) onTarget = t.direction === 'lower' ? p.avgValue <= t.targetValue : p.avgValue >= t.targetValue;
    const c = onTarget ? 'var(--teal)' : 'var(--brick)';
    return `<circle cx="${xFor(p.idx).toFixed(1)}" cy="${yFor(p.avgValue).toFixed(1)}" r="3" fill="${c}" stroke="var(--surface)" stroke-width="1.2"><title>${escapeHtml(p.periodLabel)}: ${p.avgValue}${t.unit ? ' ' + escapeHtml(t.unit) : ''}</title></circle>`;
  }).join('');

  // PDSA cycle markers — a vertical line at the cycle's start date, with a small flag at the top.
  const pdsaMarks = (markers || []).map(m => {
    const x = xFor(periodIndexForDate(periods, m.startDate)).toFixed(1);
    return `<g>
      <line x1="${x}" y1="${padT}" x2="${x}" y2="${padT + innerH}" stroke="var(--ink-faint)" stroke-width="1.2" stroke-dasharray="2 2"/>
      <circle cx="${x}" cy="${padT}" r="3" fill="var(--ink-soft)"/>
      <title>${escapeHtml(m.title)} — started ${fmtDate(m.startDate)}</title>
    </g>`;
  }).join('');

  const firstLabel = periods[0] ? periods[0].periodLabel : '';
  const lastLabel = periods[periods.length - 1] ? periods[periods.length - 1].periodLabel : '';

  return `<svg viewBox="0 0 ${w} ${h}" style="width:100%;height:${h}px;">
    <line x1="${padL}" y1="${padT}" x2="${padL}" y2="${padT + innerH}" stroke="var(--grid-line)" stroke-width="1"/>
    <line x1="${padL}" y1="${padT + innerH}" x2="${padL + innerW}" y2="${padT + innerH}" stroke="var(--grid-line)" stroke-width="1"/>
    ${hasTarget ? `<line x1="${padL}" y1="${targetY.toFixed(1)}" x2="${padL + innerW}" y2="${targetY.toFixed(1)}" stroke="var(--steel)" stroke-width="1.2" stroke-dasharray="4 3"/>
    <text x="${padL + innerW}" y="${targetY - 4}" text-anchor="end" font-size="9.5" fill="var(--steel)" font-family="var(--font-mono)">target ${t.targetValue}</text>` : ''}
    ${pdsaMarks}
    <path d="${linePath}" fill="none" stroke="var(--ink-soft)" stroke-width="1.6" stroke-linejoin="round"/>
    ${dots}
    <text x="${padL}" y="${h - 4}" text-anchor="start" font-size="9.5" fill="var(--ink-faint)" font-family="var(--font-mono)">${escapeHtml(firstLabel)}</text>
    <text x="${padL + innerW}" y="${h - 4}" text-anchor="end" font-size="9.5" fill="var(--ink-faint)" font-family="var(--font-mono)">${escapeHtml(lastLabel)}</text>
  </svg>`;
}

/* ============================================================
   MANAGE VIEW — project info, process areas, indicators, entry logs
   ============================================================ */
function renderManageView() {
  const p = dashboardData.project;
  const container = document.getElementById('viewContent');

  container.innerHTML = `
    <div class="info-grid">
      <div class="info-cell wide"><div class="label">Objectives</div><div class="value">${escapeHtml(p.objectives) || '—'}</div></div>
      <div class="info-cell"><div class="label">Start date</div><div class="value mono">${fmtDate(p.startDate)}</div></div>
      <div class="info-cell"><div class="label">Duration</div><div class="value">${escapeHtml(p.durationVal)} ${escapeHtml(p.durationUnit)}</div></div>
      <div class="info-cell"><div class="label">Reporting frequency</div><div class="value">${FREQ_LABEL[p.reportingFrequency]}</div></div>
      <div class="info-cell wide"><div class="label">Baseline status</div><div class="value">${escapeHtml(p.baseline) || '—'}</div></div>
      <div class="info-cell wide"><div class="label">What success looks like</div><div class="value">${escapeHtml(p.success) || '—'}</div></div>
    </div>

    <div class="section-head">
      <h2>Process areas &amp; indicators</h2>
      <div class="actions">
        ${p.isCreator ? `<button class="btn-secondary" onclick="openAreaModal()">+ Process area</button>` : ''}
        ${p.isCreator ? `<button class="btn-primary" onclick="openIndicatorModal()">+ Add indicator</button>` : ''}
      </div>
    </div>
    <div id="indicatorGroups"></div>

    <div class="section-head">
      <h2>PDSA cycles<span class="count">${pdsaCycles.length}</span></h2>
      <div class="actions">
        ${p.isCreator ? `<button class="btn-primary" onclick="openPdsaModal()">+ New cycle</button>` : ''}
      </div>
    </div>
    <div id="pdsaList"></div>
  `;
  renderIndicatorGroups();
  renderPdsaList();
}

function renderPdsaList() {
  const p = dashboardData.project;
  const container = document.getElementById('pdsaList');
  if (pdsaCycles.length === 0) {
    container.innerHTML = `<div class="empty-state"><h3>No PDSA cycles yet</h3><p>Document a specific test of change — what you're trying, what you predict, and what actually happened — and link it to the indicators it's meant to move.</p>${p.isCreator ? `<button class="btn-primary" onclick="openPdsaModal()">Log first cycle</button>` : ''}</div>`;
    return;
  }
  const decisionLabel = { in_progress: 'In progress', adopt: 'Adopt', adapt: 'Adapt', abandon: 'Abandon' };
  container.innerHTML = pdsaCycles.map(c => `
    <div class="pdsa-card">
      <div class="pdsa-top">
        <div>
          <h4>${escapeHtml(c.title)}</h4>
          <div class="pdsa-dates">${fmtDate(c.startDate)}${c.endDate ? ' – ' + fmtDate(c.endDate) : ' – ongoing'}${c.processAreaName ? ' · ' + escapeHtml(c.processAreaName) : ''}</div>
        </div>
        <div style="display:flex;align-items:center;gap:8px;">
          <span class="badge ${c.actDecision}">${decisionLabel[c.actDecision] || c.actDecision}</span>
          ${p.isCreator ? `<button class="btn-icon" onclick="openPdsaModal(${c.id})" aria-label="Edit cycle"><svg width="15" height="15" viewBox="0 0 15 15" fill="none"><path d="M9.5 2.5L12.5 5.5L5 13H2V10L9.5 2.5Z" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/></svg></button>
          <button class="btn-icon" onclick="confirmDeletePdsa(${c.id})" aria-label="Delete cycle"><svg width="15" height="15" viewBox="0 0 15 15" fill="none"><path d="M3 4.5H12M6 4.5V3H9V4.5M4.5 4.5L5 12.5H10L10.5 4.5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/></svg></button>` : ''}
        </div>
      </div>
      ${c.indicatorNames && c.indicatorNames.length ? `<div class="pdsa-indicators">Targets: ${c.indicatorNames.map(escapeHtml).join(', ')}</div>` : ''}
      <div class="pdsa-body">
        ${c.planText ? `<div><strong>Plan:</strong> ${escapeHtml(c.planText)}</div>` : ''}
        ${c.predictionText ? `<div><strong>Prediction:</strong> ${escapeHtml(c.predictionText)}</div>` : ''}
        ${c.doText ? `<div><strong>Do:</strong> ${escapeHtml(c.doText)}</div>` : ''}
        ${c.studyText ? `<div><strong>Study:</strong> ${escapeHtml(c.studyText)}</div>` : ''}
        ${c.actText ? `<div><strong>Act notes:</strong> ${escapeHtml(c.actText)}</div>` : ''}
      </div>
    </div>`).join('');
}

function renderIndicatorGroups() {
  const p = dashboardData.project;
  const container = document.getElementById('indicatorGroups');
  if (indicators.length === 0) {
    container.innerHTML = `<div class="empty-state"><h3>No indicators yet</h3><p>Add the process measures that contribute to this project's outcome.</p>${p.isCreator ? `<button class="btn-primary" onclick="openIndicatorModal()">Add first indicator</button>` : ''}</div>`;
    return;
  }
  const byArea = {};
  const ungrouped = [];
  for (const ind of indicators) {
    if (ind.processAreaId) { (byArea[ind.processAreaId] = byArea[ind.processAreaId] || []).push(ind); }
    else ungrouped.push(ind);
  }
  let html = '';
  for (const area of processAreas) {
    const inds = byArea[area.id] || [];
    html += `<div class="area-group">
      <div class="area-title"><h3>${escapeHtml(area.name)}</h3>
        ${p.isCreator ? `<div class="actions"><button class="btn-icon" onclick="openAreaModal(${area.id})" aria-label="Edit area"><svg width="14" height="14" viewBox="0 0 15 15" fill="none"><path d="M9.5 2.5L12.5 5.5L5 13H2V10L9.5 2.5Z" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/></svg></button>
        <button class="btn-icon" onclick="confirmDeleteArea(${area.id})" aria-label="Delete area">×</button></div>` : ''}
      </div>
      ${inds.length ? inds.map(i => renderIndicatorCard(i, p)).join('') : `<div class="faint" style="font-size:12.5px;">No indicators in this area yet.</div>`}
    </div>`;
  }
  if (ungrouped.length) {
    html += `<div class="area-group"><div class="area-title"><h3>Ungrouped</h3></div>${ungrouped.map(i => renderIndicatorCard(i, p)).join('')}</div>`;
  }
  container.innerHTML = html;
}

function renderIndicatorCard(ind, p) {
  const entries = (entriesByIndicator[ind.id] || []).slice().sort((a, b) => new Date(a.date) - new Date(b.date));
  const latest = entries.length ? entries[entries.length - 1] : null;
  const hasTarget = ind.targetValue !== null && ind.targetValue !== undefined;

  return `<div class="indicator">
    <div class="indicator-top">
      <div><h3>${escapeHtml(ind.name)}</h3><div class="optimal"><strong style="color:var(--ink-soft);">Optimal:</strong> ${escapeHtml(ind.optimal) || '—'}</div></div>
      <div class="actions">
        <button class="btn-secondary" onclick="openEntryModal(${ind.id})">Log entry</button>
        ${p.isCreator ? `<button class="btn-icon" onclick="openIndicatorModal(${ind.id})" aria-label="Edit indicator"><svg width="15" height="15" viewBox="0 0 15 15" fill="none"><path d="M9.5 2.5L12.5 5.5L5 13H2V10L9.5 2.5Z" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/></svg></button>
        <button class="btn-icon" onclick="confirmDeleteIndicator(${ind.id})" aria-label="Delete indicator"><svg width="15" height="15" viewBox="0 0 15 15" fill="none"><path d="M3 4.5H12M6 4.5V3H9V4.5M4.5 4.5L5 12.5H10L10.5 4.5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/></svg></button>` : ''}
      </div>
    </div>
    <div class="indicator-stats">
      <div class="stat"><div class="num">${latest ? latest.value : '—'}${latest && ind.unit ? ' ' + escapeHtml(ind.unit) : ''}</div><div class="lbl">Latest reading</div></div>
      <div class="stat"><div class="num">${hasTarget ? ind.targetValue + (ind.unit ? ' ' + escapeHtml(ind.unit) : '') : '—'}</div><div class="lbl">Target (${ind.direction === 'lower' ? 'lower is better' : 'higher is better'})</div></div>
      <div class="stat"><div class="num">${entries.length}</div><div class="lbl">Entries logged</div></div>
      <div class="stat"><div class="num mono" style="font-size:14px;">${latest ? fmtDate(latest.date) : '—'}</div><div class="lbl">Last reported</div></div>
    </div>
    <details class="entry-toggle">
      <summary class="btn-text" style="cursor:pointer;">View entry log</summary>
      ${entries.length === 0 ? `<div class="faint" style="font-size:12.5px;margin-top:8px;">No entries yet.</div>` : `
      <table class="entry-table">
        <thead><tr><th>Date</th><th>Numerator</th><th>Denominator</th><th>Value</th><th>Note</th><th></th></tr></thead>
        <tbody>${entries.slice().reverse().map(e => entryRow(e, ind)).join('')}</tbody>
      </table>`}
    </details>
  </div>`;
}

function elementBreakdownHtml(elements, values) {
  if (!elements.length) return '<span class="faint">—</span>';
  return elements.map(el => {
    const v = values.find(x => x.elementId === el.id);
    const amount = v ? v.amount : 0;
    const cls = el.sign === 'subtract' ? 'sub' : 'add';
    const sign = el.sign === 'subtract' ? '−' : '+';
    return `<div class="breakdown-line"><span class="${cls}">${sign}</span> ${escapeHtml(el.name)}: <strong>${amount}</strong></div>`;
  }).join('');
}

function entryRow(e, ind) {
  return `<tr>
    <td class="mono">${fmtDate(e.date)}</td>
    <td>${elementBreakdownHtml(ind.numeratorElements, e.values)}</td>
    <td>${elementBreakdownHtml(ind.denominatorElements, e.values)}</td>
    <td class="val">${e.value}${ind.unit ? ' ' + escapeHtml(ind.unit) : ''}</td>
    <td class="faint">${escapeHtml(e.note) || ''}</td>
    <td style="white-space:nowrap;">
      <button class="btn-icon" onclick="openEntryModal(${ind.id}, ${e.id})" aria-label="Edit entry"><svg width="13" height="13" viewBox="0 0 15 15" fill="none"><path d="M9.5 2.5L12.5 5.5L5 13H2V10L9.5 2.5Z" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/></svg></button>
      <button class="btn-icon" onclick="deleteEntry(${e.id})" aria-label="Delete entry"><svg width="13" height="13" viewBox="0 0 15 15" fill="none"><path d="M3 4.5H12M6 4.5V3H9V4.5M4.5 4.5L5 12.5H10L10.5 4.5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/></svg></button>
    </td>
  </tr>`;
}

/* ============================================================
   MODAL / PILL UTIL
   ============================================================ */
function openModal(id) { document.getElementById(id).classList.add('open'); }
function closeModal(id) { document.getElementById(id).classList.remove('open'); }
document.querySelectorAll('.overlay').forEach(ov => ov.addEventListener('click', e => { if (e.target === ov) ov.classList.remove('open'); }));
document.querySelectorAll('.radio-group').forEach(group => {
  group.addEventListener('click', e => {
    const pill = e.target.closest('.radio-pill');
    if (!pill) return;
    [...group.children].forEach(c => c.classList.remove('selected'));
    pill.classList.add('selected');
  });
});
function setPillGroup(groupId, value) { const g = document.getElementById(groupId); [...g.children].forEach(c => c.classList.toggle('selected', c.dataset.value === value)); }
function getPillGroup(groupId) { const sel = document.querySelector('#' + groupId + ' .selected'); return sel ? sel.dataset.value : null; }
function toggleSidebar() { document.getElementById('sidebar').classList.toggle('open'); }

/* ============================================================
   PROJECT MODAL
   ============================================================ */
function openProjectModal(id) {
  editingProjectId = id || null;
  document.getElementById('projectModalTitle').textContent = id ? 'Edit project' : 'New project';
  if (id) {
    const p = dashboardData && dashboardData.project.id == id ? dashboardData.project : projects.find(x => x.id == id);
    document.getElementById('pf-name').value = p.name;
    document.getElementById('pf-objectives').value = p.objectives || '';
    document.getElementById('pf-start').value = p.startDate || '';
    document.getElementById('pf-duration-val').value = p.durationVal || '';
    document.getElementById('pf-duration-unit').value = p.durationUnit || 'months';
    document.getElementById('pf-baseline').value = p.baseline || '';
    document.getElementById('pf-success').value = p.success || '';
    setPillGroup('pf-frequency', p.reportingFrequency);
  } else {
    document.getElementById('pf-name').value = '';
    document.getElementById('pf-objectives').value = '';
    document.getElementById('pf-start').value = new Date().toISOString().slice(0, 10);
    document.getElementById('pf-duration-val').value = '';
    document.getElementById('pf-duration-unit').value = 'months';
    document.getElementById('pf-baseline').value = '';
    document.getElementById('pf-success').value = '';
    setPillGroup('pf-frequency', 'weekly');
  }
  openModal('projectOverlay');
}

async function saveProject() {
  const btn = document.getElementById('saveProjectBtn');
  if (btn.disabled) return;
  const name = document.getElementById('pf-name').value.trim();
  if (!name) { alert('Give the project a name.'); return; }
  const data = {
    name,
    objectives: document.getElementById('pf-objectives').value.trim(),
    startDate: document.getElementById('pf-start').value,
    durationVal: document.getElementById('pf-duration-val').value || '',
    durationUnit: document.getElementById('pf-duration-unit').value,
    baseline: document.getElementById('pf-baseline').value.trim(),
    success: document.getElementById('pf-success').value.trim(),
    reportingFrequency: getPillGroup('pf-frequency'),
  };
  btn.disabled = true;
  const originalLabel = btn.textContent;
  btn.textContent = 'Saving…';
  try {
    if (editingProjectId) {
      await api('/api/projects/' + editingProjectId, { method: 'PUT', body: JSON.stringify(data) });
      closeModal('projectOverlay');
      await loadProjectList();
      await loadDashboard();
      renderProjectShell();
    } else {
      const created = await api('/api/projects', { method: 'POST', body: JSON.stringify(data) });
      closeModal('projectOverlay');
      await loadProjectList();
      await openProject(created.id);
    }
  } catch (err) { alert(err.message); }
  finally { btn.disabled = false; btn.textContent = originalLabel; }
}

function confirmDeleteProject(id) {
  const p = dashboardData.project;
  document.getElementById('confirmTitle').textContent = `Delete "${p.name}"?`;
  document.getElementById('confirmBody').textContent = 'This removes the project, its process areas, indicators, and all logged entries, for everyone on the team. This can\'t be undone.';
  const btn = document.getElementById('confirmActionBtn');
  btn.onclick = async () => {
    if (btn.disabled) return;
    btn.disabled = true;
    try {
      await api('/api/projects/' + id, { method: 'DELETE' });
      closeModal('confirmOverlay');
      await loadProjectList();
      renderProjectListView();
    } catch (err) { alert(err.message); }
    finally { btn.disabled = false; }
  };
  openModal('confirmOverlay');
}

async function sendReportNow() {
  try {
    await api(`/api/projects/${currentProjectId}/send-report`, { method: 'POST' });
    alert('Report sent to everyone assigned to this project.');
  } catch (err) { alert(err.message); }
}

/* ============================================================
   PROCESS AREA MODAL
   ============================================================ */
function openAreaModal(id) {
  editingAreaId = id || null;
  document.getElementById('areaModalTitle').textContent = id ? 'Edit process area' : 'New process area';
  if (id) {
    const area = processAreas.find(a => a.id === id);
    document.getElementById('af-name').value = area.name;
    document.getElementById('af-description').value = area.description || '';
  } else {
    document.getElementById('af-name').value = '';
    document.getElementById('af-description').value = '';
  }
  openModal('areaOverlay');
}

async function saveArea() {
  const name = document.getElementById('af-name').value.trim();
  if (!name) { alert('Give the process area a name.'); return; }
  const data = { name, description: document.getElementById('af-description').value.trim() };
  const btn = document.getElementById('saveAreaBtn');
  if (btn.disabled) return;
  btn.disabled = true;
  const originalLabel = btn.textContent;
  btn.textContent = 'Saving…';
  try {
    if (editingAreaId) await api(`/api/projects/${currentProjectId}/process-areas/${editingAreaId}`, { method: 'PUT', body: JSON.stringify(data) });
    else await api(`/api/projects/${currentProjectId}/process-areas`, { method: 'POST', body: JSON.stringify(data) });
    closeModal('areaOverlay');
    await loadManageData();
    renderIndicatorGroups();
  } catch (err) { alert(err.message); }
  finally { btn.disabled = false; btn.textContent = originalLabel; }
}

function confirmDeleteArea(id) {
  const area = processAreas.find(a => a.id === id);
  document.getElementById('confirmTitle').textContent = `Delete "${area.name}"?`;
  document.getElementById('confirmBody').textContent = 'Indicators in this area become ungrouped rather than being deleted.';
  document.getElementById('confirmActionBtn').onclick = async () => {
    try {
      await api(`/api/projects/${currentProjectId}/process-areas/${id}`, { method: 'DELETE' });
      closeModal('confirmOverlay');
      await loadManageData();
      renderIndicatorGroups();
    } catch (err) { alert(err.message); }
  };
  openModal('confirmOverlay');
}

/* ============================================================
   INDICATOR MODAL — numerator/denominator element builder
   ============================================================ */
function uid(prefix) { return prefix + '_' + Math.random().toString(36).slice(2, 10) + Date.now().toString(36).slice(-4); }
function elArr(section) { return section === 'numerator' ? tempNumerator : tempDenominator; }

function addElementRow(section) { elArr(section).push({ id: null, clientId: uid('el'), name: '', sign: 'add' }); renderElementBuilder(section); }
function removeElementRow(section, clientId) { const arr = elArr(section); const idx = arr.findIndex(e => e.clientId === clientId); if (idx > -1) arr.splice(idx, 1); renderElementBuilder(section); }
function updateElementName(section, clientId, name) { const el = elArr(section).find(e => e.clientId === clientId); if (el) el.name = name; updateFormulaPreview(); }
function toggleElementSign(section, clientId) { const el = elArr(section).find(e => e.clientId === clientId); if (el) el.sign = el.sign === 'add' ? 'subtract' : 'add'; renderElementBuilder(section); }

function renderElementBuilder(section) {
  const arr = elArr(section);
  const container = document.getElementById(section === 'numerator' ? 'numeratorElements' : 'denominatorElements');
  if (arr.length === 0) {
    container.innerHTML = `<div class="faint" style="font-size:12px;padding:2px 0 6px;">${section === 'numerator' ? 'Add at least one data element.' : 'None — this indicator will be a straight count.'}</div>`;
  } else {
    container.innerHTML = arr.map(el => `
      <div class="element-row">
        <button type="button" class="sign-toggle ${el.sign}" onclick="toggleElementSign('${section}','${el.clientId}')" aria-label="Toggle sign">${el.sign === 'add' ? '+' : '−'}</button>
        <input type="text" placeholder="e.g. Patients with documented consent" value="${escapeHtml(el.name)}" oninput="updateElementName('${section}','${el.clientId}', this.value)">
        <button type="button" class="btn-icon" onclick="removeElementRow('${section}','${el.clientId}')" aria-label="Remove">×</button>
      </div>`).join('');
  }
  updateFormulaPreview();
}

function updateFormulaPreview() {
  const fmt = arr => arr.map((el, idx) => {
    const label = el.name.trim() || '…';
    if (idx === 0) return el.sign === 'subtract' ? `−${label}` : label;
    return el.sign === 'subtract' ? ` − ${label}` : ` + ${label}`;
  }).join('');
  const numStr = tempNumerator.length ? fmt(tempNumerator) : '…';
  const denStr = tempDenominator.length ? fmt(tempDenominator) : null;
  const multEl = document.getElementById('if-multiplier');
  const mult = multEl ? multEl.value : '1';
  let formula = denStr ? `(${numStr}) / (${denStr})` : numStr;
  if (mult && mult !== '1') formula += ` × ${Number(mult).toLocaleString()}`;
  const target = document.getElementById('formulaPreview');
  if (target) target.textContent = formula;
}

function computeValue(indicator, amountsByElementId) {
  const sumSigned = elements => (elements || []).reduce((acc, el) => acc + (el.sign === 'subtract' ? -1 : 1) * (Number(amountsByElementId[el.id]) || 0), 0);
  const numSum = sumSigned(indicator.numeratorElements);
  const denEls = indicator.denominatorElements || [];
  const mult = indicator.multiplier || 1;
  if (denEls.length === 0) return round2(numSum * mult);
  const denSum = sumSigned(denEls);
  if (!denSum) return 0;
  return round2((numSum / denSum) * mult);
}

function populateAreaDropdown(selectedId) {
  const sel = document.getElementById('if-area');
  sel.innerHTML = '<option value="">— Ungrouped —</option>' + processAreas.map(a => `<option value="${a.id}">${escapeHtml(a.name)}</option>`).join('');
  sel.value = selectedId ? String(selectedId) : '';
}

function openIndicatorModal(id) {
  editingIndicatorId = id || null;
  document.getElementById('indicatorModalTitle').textContent = id ? 'Edit indicator' : 'New process indicator';
  if (id) {
    const ind = indicators.find(x => x.id === id);
    document.getElementById('if-name').value = ind.name;
    document.getElementById('if-optimal').value = ind.optimal || '';
    document.getElementById('if-target').value = ind.targetValue ?? '';
    document.getElementById('if-unit').value = ind.unit || '';
    document.getElementById('if-multiplier').value = String(ind.multiplier || 1);
    setPillGroup('if-direction', ind.direction);
    populateAreaDropdown(ind.processAreaId);
    tempNumerator = (ind.numeratorElements || []).map(e => ({ id: e.id, clientId: uid('el'), name: e.name, sign: e.sign }));
    tempDenominator = (ind.denominatorElements || []).map(e => ({ id: e.id, clientId: uid('el'), name: e.name, sign: e.sign }));
    if (tempNumerator.length === 0) tempNumerator = [{ id: null, clientId: uid('el'), name: '', sign: 'add' }];
  } else {
    document.getElementById('if-name').value = '';
    document.getElementById('if-optimal').value = '';
    document.getElementById('if-target').value = '';
    document.getElementById('if-unit').value = '';
    document.getElementById('if-multiplier').value = '1';
    setPillGroup('if-direction', 'higher');
    populateAreaDropdown(null);
    tempNumerator = [{ id: null, clientId: uid('el'), name: '', sign: 'add' }];
    tempDenominator = [];
  }
  renderElementBuilder('numerator');
  renderElementBuilder('denominator');
  openModal('indicatorOverlay');
}

async function saveIndicator() {
  const name = document.getElementById('if-name').value.trim();
  if (!name) { alert('Give the indicator a name.'); return; }
  const toReq = arr => arr.filter(e => e.name.trim() !== '').map(e => ({ id: e.id, name: e.name.trim(), sign: e.sign }));
  const numeratorElements = toReq(tempNumerator);
  const denominatorElements = toReq(tempDenominator);
  if (numeratorElements.length === 0) { alert('Add at least one numerator data element.'); return; }
  const areaVal = document.getElementById('if-area').value;
  const direction = getPillGroup('if-direction');
  const data = {
    name,
    optimal: document.getElementById('if-optimal').value.trim(),
    processAreaId: areaVal ? Number(areaVal) : null,
    numeratorElements, denominatorElements,
    multiplier: Number(document.getElementById('if-multiplier').value) || 1,
    targetValue: document.getElementById('if-target').value === '' ? null : Number(document.getElementById('if-target').value),
    unit: document.getElementById('if-unit').value.trim(),
    direction,
  };
  const btn = document.getElementById('saveIndicatorBtn');
  if (btn.disabled) return;
  btn.disabled = true;
  const originalLabel = btn.textContent;
  btn.textContent = 'Saving…';
  try {
    if (editingIndicatorId) await api('/api/indicators/' + editingIndicatorId, { method: 'PUT', body: JSON.stringify(data) });
    else await api(`/api/projects/${currentProjectId}/indicators`, { method: 'POST', body: JSON.stringify(data) });
    closeModal('indicatorOverlay');
    await loadManageData();
    renderIndicatorGroups();
  } catch (err) { alert(err.message); }
  finally { btn.disabled = false; btn.textContent = originalLabel; }
}

function confirmDeleteIndicator(id) {
  const ind = indicators.find(x => x.id === id);
  document.getElementById('confirmTitle').textContent = `Delete "${ind.name}"?`;
  document.getElementById('confirmBody').textContent = 'This removes the indicator and all of its logged entries, for everyone on the team. This can\'t be undone.';
  document.getElementById('confirmActionBtn').onclick = async () => {
    try {
      await api('/api/indicators/' + id, { method: 'DELETE' });
      closeModal('confirmOverlay');
      await loadManageData();
      renderIndicatorGroups();
    } catch (err) { alert(err.message); }
  };
  openModal('confirmOverlay');
}

/* ============================================================
   ENTRY MODAL (create + edit) — shows numerator/denominator breakdown live
   ============================================================ */
function openEntryModal(indicatorId, entryId) {
  activeIndicatorForEntry = indicatorId;
  editingEntryId = entryId || null;
  const ind = indicators.find(i => i.id === indicatorId);
  document.getElementById('entryModalTitle').textContent = entryId ? 'Edit entry' : 'Log progress';
  document.getElementById('ef-note').value = '';
  document.getElementById('ef-date').value = new Date().toISOString().slice(0, 10);

  let existingValues = {};
  if (entryId) {
    const entry = (entriesByIndicator[indicatorId] || []).find(e => e.id === entryId);
    if (entry) {
      document.getElementById('ef-date').value = entry.date;
      document.getElementById('ef-note').value = entry.note || '';
      entry.values.forEach(v => { existingValues[v.elementId] = v.amount; });
    }
  }
  renderEntryFields(ind, existingValues);
  openModal('entryOverlay');
}

function renderEntryFields(ind, existingValues) {
  const container = document.getElementById('entryFieldsContainer');
  const rowHtml = el => `<div class="entry-el-row">
      <span class="el-sign">${el.sign === 'subtract' ? '−' : '+'}</span>
      <span class="el-name">${escapeHtml(el.name)}</span>
      <input type="number" step="any" class="el-input" data-id="${el.id}" oninput="updateEntryPreview()" value="${existingValues[el.id] ?? ''}" placeholder="0">
    </div>`;
  let html = `<div class="entry-section-label">Numerator</div>` + ind.numeratorElements.map(rowHtml).join('');
  if (ind.denominatorElements && ind.denominatorElements.length) {
    html += `<div class="entry-section-label">Denominator</div>` + ind.denominatorElements.map(rowHtml).join('');
  }
  container.innerHTML = html;
  updateEntryPreview();
}

function updateEntryPreview() {
  const ind = indicators.find(i => i.id === activeIndicatorForEntry);
  const amounts = {};
  document.querySelectorAll('#entryFieldsContainer .el-input').forEach(inp => { amounts[inp.dataset.id] = inp.value; });
  const val = computeValue(ind, amounts);
  document.getElementById('entryComputedPreview').innerHTML =
    `<div class="label">Resulting value</div><div class="mono" style="font-size:18px;font-weight:600;">${isFinite(val) ? val : '0'}${ind.unit ? ' ' + escapeHtml(ind.unit) : ''}</div>`;
}

async function saveEntry() {
  const btn = document.getElementById('saveEntryBtn');
  if (btn.disabled) return; // already submitting — ignore a rapid second click
  const date = document.getElementById('ef-date').value;
  if (!date) { alert('Add a date.'); return; }
  const amounts = {};
  document.querySelectorAll('#entryFieldsContainer .el-input').forEach(inp => { amounts[Number(inp.dataset.id)] = Number(inp.value) || 0; });
  const values = Object.entries(amounts).map(([elementId, amount]) => ({ elementId: Number(elementId), amount }));
  const note = document.getElementById('ef-note').value.trim();
  const payload = { date, values, note };
  btn.disabled = true;
  const originalLabel = btn.textContent;
  btn.textContent = 'Saving…';
  try {
    if (editingEntryId) await api('/api/entries/' + editingEntryId, { method: 'PUT', body: JSON.stringify(payload) });
    else await api(`/api/indicators/${activeIndicatorForEntry}/entries`, { method: 'POST', body: JSON.stringify(payload) });
    closeModal('entryOverlay');
    entriesByIndicator[activeIndicatorForEntry] = await api(`/api/indicators/${activeIndicatorForEntry}/entries`);
    renderIndicatorGroups();
  } catch (err) { alert(err.message); }
  finally { btn.disabled = false; btn.textContent = originalLabel; }
}

async function deleteEntry(entryId) {
  try {
    await api('/api/entries/' + entryId, { method: 'DELETE' });
    for (const key of Object.keys(entriesByIndicator)) {
      entriesByIndicator[key] = entriesByIndicator[key].filter(e => e.id !== entryId);
    }
    renderIndicatorGroups();
  } catch (err) { alert(err.message); }
}

/* ============================================================
   MEMBERS MODAL
   ============================================================ */
async function openMembersModal() {
  document.getElementById('mf-email').value = '';
  try {
    members = await api(`/api/projects/${currentProjectId}/members`);
    renderMemberList();
    openModal('membersOverlay');
  } catch (err) { alert(err.message); }
}

function renderMemberList() {
  const list = document.getElementById('memberList');
  list.innerHTML = members.map(m => `
    <div class="member-row">
      <div class="info"><span class="name">${escapeHtml(m.name)} ${m.isCreator ? '<span class="faint">(creator)</span>' : ''}</span><span class="email">${escapeHtml(m.email)}</span></div>
      ${m.isCreator ? '' : `<button class="btn-text danger" onclick="removeMember(${m.userId})">Remove</button>`}
    </div>`).join('');
}

async function addMember() {
  const email = document.getElementById('mf-email').value.trim();
  if (!email) { alert('Enter an email.'); return; }
  try {
    await api(`/api/projects/${currentProjectId}/members`, { method: 'POST', body: JSON.stringify({ email }) });
    document.getElementById('mf-email').value = '';
    members = await api(`/api/projects/${currentProjectId}/members`);
    renderMemberList();
  } catch (err) { alert(err.message); }
}

async function removeMember(userId) {
  try {
    await api(`/api/projects/${currentProjectId}/members/${userId}`, { method: 'DELETE' });
    members = members.filter(m => m.userId !== userId);
    renderMemberList();
  } catch (err) { alert(err.message); }
}

/* ============================================================
   PDSA CYCLE MODAL
   ============================================================ */
function renderPdsaIndicatorChecklist(selectedIds) {
  const container = document.getElementById('pd-indicators');
  const selected = new Set(selectedIds || []);
  if (indicators.length === 0) {
    container.innerHTML = `<div class="empty">Add an indicator first — a cycle needs something to link to.</div>`;
    return;
  }
  container.innerHTML = indicators.map(ind => `
    <label>
      <input type="checkbox" value="${ind.id}" ${selected.has(ind.id) ? 'checked' : ''}>
      ${escapeHtml(ind.name)}
    </label>`).join('');
}

function openPdsaModal(id) {
  editingPdsaId = id || null;
  document.getElementById('pdsaModalTitle').textContent = id ? 'Edit PDSA cycle' : 'New PDSA cycle';
  populateAreaDropdown2('pd-area', null);

  if (id) {
    const c = pdsaCycles.find(x => x.id === id);
    document.getElementById('pd-title').value = c.title;
    document.getElementById('pd-start').value = c.startDate;
    document.getElementById('pd-end').value = c.endDate || '';
    document.getElementById('pd-area').value = c.processAreaId ? String(c.processAreaId) : '';
    document.getElementById('pd-plan').value = c.planText || '';
    document.getElementById('pd-prediction').value = c.predictionText || '';
    document.getElementById('pd-do').value = c.doText || '';
    document.getElementById('pd-study').value = c.studyText || '';
    document.getElementById('pd-act-text').value = c.actText || '';
    setPillGroup('pd-decision', c.actDecision);
    renderPdsaIndicatorChecklist(c.indicatorIds);
  } else {
    document.getElementById('pd-title').value = '';
    document.getElementById('pd-start').value = new Date().toISOString().slice(0, 10);
    document.getElementById('pd-end').value = '';
    document.getElementById('pd-area').value = '';
    document.getElementById('pd-plan').value = '';
    document.getElementById('pd-prediction').value = '';
    document.getElementById('pd-do').value = '';
    document.getElementById('pd-study').value = '';
    document.getElementById('pd-act-text').value = '';
    setPillGroup('pd-decision', 'in_progress');
    renderPdsaIndicatorChecklist([]);
  }
  openModal('pdsaOverlay');
}

// populateAreaDropdown() already exists for the indicator modal (id="if-area") — this is the same
// idea for the PDSA modal's own <select>, kept separate since they're different DOM elements.
function populateAreaDropdown2(selectId, selectedId) {
  const sel = document.getElementById(selectId);
  sel.innerHTML = '<option value="">— None —</option>' + processAreas.map(a => `<option value="${a.id}">${escapeHtml(a.name)}</option>`).join('');
  sel.value = selectedId ? String(selectedId) : '';
}

async function savePdsaCycle() {
  const title = document.getElementById('pd-title').value.trim();
  if (!title) { alert('Give the cycle a short title.'); return; }
  const startDate = document.getElementById('pd-start').value;
  if (!startDate) { alert('Add a start date.'); return; }
  const areaVal = document.getElementById('pd-area').value;
  const indicatorIds = Array.from(document.querySelectorAll('#pd-indicators input[type=checkbox]:checked')).map(cb => Number(cb.value));

  const data = {
    title,
    processAreaId: areaVal ? Number(areaVal) : null,
    startDate,
    endDate: document.getElementById('pd-end').value || null,
    planText: document.getElementById('pd-plan').value.trim(),
    predictionText: document.getElementById('pd-prediction').value.trim(),
    doText: document.getElementById('pd-do').value.trim(),
    studyText: document.getElementById('pd-study').value.trim(),
    actDecision: getPillGroup('pd-decision'),
    actText: document.getElementById('pd-act-text').value.trim(),
    indicatorIds,
  };

  const btn = document.getElementById('savePdsaBtn');
  if (btn.disabled) return;
  btn.disabled = true;
  const originalLabel = btn.textContent;
  btn.textContent = 'Saving…';
  try {
    if (editingPdsaId) await api('/api/pdsa-cycles/' + editingPdsaId, { method: 'PUT', body: JSON.stringify(data) });
    else await api(`/api/projects/${currentProjectId}/pdsa-cycles`, { method: 'POST', body: JSON.stringify(data) });
    closeModal('pdsaOverlay');
    pdsaCycles = await api(`/api/projects/${currentProjectId}/pdsa-cycles`);
    renderPdsaList();
  } catch (err) { alert(err.message); }
  finally { btn.disabled = false; btn.textContent = originalLabel; }
}

function confirmDeletePdsa(id) {
  const c = pdsaCycles.find(x => x.id === id);
  document.getElementById('confirmTitle').textContent = `Delete "${c.title}"?`;
  document.getElementById('confirmBody').textContent = 'This removes the PDSA cycle, including its plan/study notes and its links to indicators. This can\'t be undone.';
  document.getElementById('confirmActionBtn').onclick = async () => {
    try {
      await api('/api/pdsa-cycles/' + id, { method: 'DELETE' });
      closeModal('confirmOverlay');
      pdsaCycles = pdsaCycles.filter(x => x.id !== id);
      renderPdsaList();
    } catch (err) { alert(err.message); }
  };
  openModal('confirmOverlay');
}