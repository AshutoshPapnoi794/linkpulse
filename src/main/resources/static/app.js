'use strict';

// The frontend is served by the same Spring Boot origin, so relative API paths
// avoid CORS configuration. Change this only when hosting the UI separately.
const API_BASE = '';
const TOKEN_KEY = 'linkpulse_access_token';
const PAGE_SIZE = 20;

const state = {
  token: sessionStorage.getItem(TOKEN_KEY),
  user: null,
  page: 0,
  totalPages: 0,
  links: [],
  toastTimer: null
};

const elements = {
  authView: document.getElementById('authView'),
  dashboardView: document.getElementById('dashboardView'),
  loginForm: document.getElementById('loginForm'),
  registerForm: document.getElementById('registerForm'),
  loginTab: document.getElementById('loginTab'),
  registerTab: document.getElementById('registerTab'),
  authHeading: document.getElementById('authHeading'),
  authEyebrow: document.getElementById('authEyebrow'),
  authSubheading: document.getElementById('authSubheading'),
  userEmail: document.getElementById('userEmail'),
  userAvatar: document.getElementById('userAvatar'),
  todayLabel: document.getElementById('todayLabel'),
  createForm: document.getElementById('createForm'),
  linksTableBody: document.getElementById('linksTableBody'),
  linksSubheading: document.getElementById('linksSubheading'),
  emptyState: document.getElementById('emptyState'),
  totalLinks: document.getElementById('totalLinks'),
  activeLinks: document.getElementById('activeLinks'),
  totalClicks: document.getElementById('totalClicks'),
  paginationInfo: document.getElementById('paginationInfo'),
  previousPage: document.getElementById('previousPage'),
  nextPage: document.getElementById('nextPage'),
  refreshButton: document.getElementById('refreshButton'),
  logoutButton: document.getElementById('logoutButton'),
  linkSearch: document.getElementById('linkSearch'),
  toast: document.getElementById('toast'),
  analyticsDialog: document.getElementById('analyticsDialog'),
  analyticsTitle: document.getElementById('analyticsTitle'),
  analyticsSubtitle: document.getElementById('analyticsSubtitle'),
  analyticsTotal: document.getElementById('analyticsTotal'),
  analyticsPeriod: document.getElementById('analyticsPeriod'),
  dailyChart: document.getElementById('dailyChart')
};

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  })[character]);
}

function showToast(message, isError = false) {
  window.clearTimeout(state.toastTimer);
  elements.toast.textContent = message;
  elements.toast.classList.toggle('is-error', isError);
  elements.toast.classList.add('is-visible');
  state.toastTimer = window.setTimeout(() => {
    elements.toast.classList.remove('is-visible');
  }, 3200);
}

async function parseResponse(response) {
  const contentType = response.headers.get('content-type') || '';
  if (response.status === 204) return null;
  if (contentType.includes('application/json') || contentType.includes('application/problem+json')) {
    return response.json();
  }
  return response.text();
}

function errorMessage(body, fallback) {
  if (!body) return fallback;
  if (typeof body === 'string') return body || fallback;
  if (body.errors && typeof body.errors === 'object') {
    const firstError = Object.values(body.errors)[0];
    if (firstError) return String(firstError);
  }
  return body.detail || body.message || body.title || fallback;
}

async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set('Accept', 'application/json');

  if (options.body !== undefined && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  const authenticated = options.auth !== false;
  if (authenticated && state.token) {
    headers.set('Authorization', `Bearer ${state.token}`);
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
    body: options.body instanceof FormData
      ? options.body
      : options.body === undefined
        ? undefined
        : JSON.stringify(options.body)
  });

  const body = await parseResponse(response);

  if (response.status === 401 && authenticated && state.token) {
    clearSession();
    showAuth();
    showToast('Your session has expired. Please sign in again.', true);
  }

  if (!response.ok) {
    const error = new Error(errorMessage(body, `Request failed (${response.status})`));
    error.status = response.status;
    error.body = body;
    throw error;
  }

  return body;
}

function setAuthMode(mode) {
  const isLogin = mode === 'login';
  elements.loginForm.hidden = !isLogin;
  elements.registerForm.hidden = isLogin;
  elements.loginTab.classList.toggle('is-active', isLogin);
  elements.registerTab.classList.toggle('is-active', !isLogin);
  elements.loginTab.setAttribute('aria-selected', String(isLogin));
  elements.registerTab.setAttribute('aria-selected', String(!isLogin));
  elements.authEyebrow.textContent = isLogin ? 'WELCOME BACK' : 'GET STARTED';
  elements.authHeading.textContent = isLogin ? 'Sign in to LinkPulse' : 'Create your account';
  elements.authSubheading.textContent = isLogin
    ? 'Your links are right where you left them.'
    : 'A simpler way to manage the links you share.';
}

function showAuth() {
  elements.dashboardView.hidden = true;
  elements.authView.hidden = false;
  setAuthMode('login');
}

function showDashboard() {
  elements.authView.hidden = true;
  elements.dashboardView.hidden = false;
  const email = state.user?.email || 'Your account';
  elements.userEmail.textContent = email;
  elements.userEmail.title = email;
  elements.userAvatar.textContent = email.trim().charAt(0).toUpperCase() || 'U';
  elements.todayLabel.textContent = new Intl.DateTimeFormat(undefined, {
    weekday: 'short', month: 'short', day: 'numeric'
  }).format(new Date());
}

function saveSession(data) {
  if (!data?.accessToken) throw new Error('The server did not return an access token.');
  state.token = data.accessToken;
  state.user = data.user || null;
  sessionStorage.setItem(TOKEN_KEY, state.token);
}

function clearSession() {
  state.token = null;
  state.user = null;
  state.links = [];
  sessionStorage.removeItem(TOKEN_KEY);
}

async function authenticate(mode, form) {
  const formData = new FormData(form);
  const payload = {
    email: String(formData.get('email') || '').trim(),
    password: String(formData.get('password') || '')
  };

  const endpoint = mode === 'register'
    ? '/api/v1/auth/register'
    : '/api/v1/auth/login';

  const submitButton = form.querySelector('button[type="submit"]');
  setButtonBusy(submitButton, true, mode === 'register' ? 'Creating…' : 'Signing in…');

  try {
    const result = await api(endpoint, {
      method: 'POST',
      body: payload,
      auth: false
    });
    saveSession(result);

    if (!state.user) {
      state.user = await api('/api/v1/auth/me');
    }

    showDashboard();
    await loadLinks();
    showToast(mode === 'register' ? 'Your account is ready.' : 'Welcome back.');
  } catch (error) {
    showToast(error.message || 'Authentication failed.', true);
  } finally {
    setButtonBusy(submitButton, false);
  }
}

function setButtonBusy(button, busy, busyText = 'Working…') {
  if (!button) return;
  if (busy) {
    button.dataset.originalText = button.textContent;
    button.textContent = busyText;
    button.disabled = true;
  } else {
    button.textContent = button.dataset.originalText || button.textContent;
    button.disabled = false;
    delete button.dataset.originalText;
  }
}

function formatNumber(value) {
  return new Intl.NumberFormat().format(Number(value) || 0);
}

function formatDate(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat(undefined, {
    year: 'numeric', month: 'short', day: 'numeric'
  }).format(date);
}

function linkStatus(link) {
  if (!link.active) return { label: 'Disabled', className: 'status-inactive' };
  if (link.expiresAt && new Date(link.expiresAt).getTime() <= Date.now()) {
    return { label: 'Expired', className: 'status-expired' };
  }
  return { label: 'Active', className: 'status-active' };
}

function renderLinks() {
  const query = elements.linkSearch.value.trim().toLowerCase();
  const filtered = state.links.filter(link => {
    return [link.shortCode, link.originalUrl, link.shortUrl]
      .some(value => String(value || '').toLowerCase().includes(query));
  });

  const activeCount = state.links.filter(link => linkStatus(link).label === 'Active').length;
  const clickCount = state.links.reduce((sum, link) => sum + (Number(link.clickCount) || 0), 0);

  elements.totalLinks.textContent = formatNumber(state.totalElements || 0);
  elements.activeLinks.textContent = formatNumber(activeCount);
  elements.totalClicks.textContent = formatNumber(clickCount);
  elements.linksSubheading.textContent = query
    ? `${filtered.length} matching link${filtered.length === 1 ? '' : 's'} on this page.`
    : 'A tidy list of everything you\'ve shortened.';

  if (filtered.length === 0) {
    elements.linksTableBody.innerHTML = '';
    elements.emptyState.hidden = false;
    elements.emptyState.querySelector('h3').textContent = query ? 'No matching links' : 'No links here yet';
    elements.emptyState.querySelector('p').textContent = query
      ? 'Try another search term.'
      : 'Create your first short link above. It\'ll show up here.';
  } else {
    elements.emptyState.hidden = true;
    elements.linksTableBody.innerHTML = filtered.map(link => {
      const status = linkStatus(link);
      const shortUrl = String(link.shortUrl || '');
      const destination = String(link.originalUrl || '');
      const safeShortUrl = escapeHtml(shortUrl);
      const safeDestination = escapeHtml(destination);
      const safeCode = escapeHtml(link.shortCode);
      const id = Number(link.id);
      const disabledAction = status.label !== 'Active';

      return `
        <tr>
          <td class="short-link-cell">
            <a class="short-link-main" href="${safeShortUrl}" target="_blank" rel="noopener noreferrer" title="${safeShortUrl}">${safeShortUrl}</a>
            <span class="short-link-code">/${safeCode}</span>
          </td>
          <td class="destination-cell" title="${safeDestination}">${safeDestination}</td>
          <td class="click-cell">${formatNumber(link.clickCount)}</td>
          <td><span class="status-pill ${status.className}">${status.label}</span></td>
          <td>${formatDate(link.createdAt)}</td>
          <td class="action-cell">
            <div class="row-actions">
              <button class="text-action" type="button" data-action="copy" data-id="${id}">Copy</button>
              <button class="text-action" type="button" data-action="analytics" data-id="${id}">Stats</button>
              ${disabledAction ? '' : `<button class="text-action danger" type="button" data-action="deactivate" data-id="${id}">Disable</button>`}
            </div>
          </td>
        </tr>`;
    }).join('');
  }

  const total = Number(state.totalElements) || 0;
  const start = total === 0 ? 0 : state.page * PAGE_SIZE + 1;
  const end = Math.min((state.page + 1) * PAGE_SIZE, total);
  elements.paginationInfo.textContent = total === 0
    ? 'No links to show'
    : `Showing ${start}–${end} of ${formatNumber(total)}`;
  elements.previousPage.disabled = state.page <= 0;
  elements.nextPage.disabled = state.page + 1 >= state.totalPages;
}

async function loadLinks() {
  elements.linksTableBody.innerHTML = '<tr><td colspan="6" class="table-placeholder">Loading your links…</td></tr>';
  elements.emptyState.hidden = true;
  try {
    const result = await api(`/api/v1/urls?page=${state.page}&size=${PAGE_SIZE}`);
    state.links = Array.isArray(result?.content) ? result.content : [];
    state.totalElements = Number(result?.totalElements) || 0;
    state.totalPages = Number(result?.totalPages) || 0;
    renderLinks();
  } catch (error) {
    elements.linksTableBody.innerHTML = '';
    elements.emptyState.hidden = false;
    elements.emptyState.querySelector('h3').textContent = 'Could not load links';
    elements.emptyState.querySelector('p').textContent = error.message || 'Please refresh and try again.';
    showToast(error.message || 'Could not load your links.', true);
  }
}

async function createLink(event) {
  event.preventDefault();
  const formData = new FormData(elements.createForm);
  const originalUrl = String(formData.get('originalUrl') || '').trim();
  const customAlias = String(formData.get('customAlias') || '').trim();
  const expiresAtInput = String(formData.get('expiresAt') || '').trim();
  const payload = { originalUrl };

  if (customAlias) payload.customAlias = customAlias;
  if (expiresAtInput) {
    const date = new Date(expiresAtInput);
    if (Number.isNaN(date.getTime())) {
      showToast('Please enter a valid expiration date.', true);
      return;
    }
    payload.expiresAt = date.toISOString();
  }

  const button = elements.createForm.querySelector('button[type="submit"]');
  setButtonBusy(button, true, 'Creating…');

  try {
    const result = await api('/api/v1/urls', {
      method: 'POST',
      body: payload
    });
    elements.createForm.reset();
    state.page = 0;
    await loadLinks();
    showToast(`Short link created: ${result.shortCode}`);
  } catch (error) {
    showToast(error.message || 'Could not create the link.', true);
  } finally {
    setButtonBusy(button, false);
  }
}

async function copyLink(link) {
  const value = link?.shortUrl;
  if (!value) return;
  try {
    await navigator.clipboard.writeText(value);
    showToast('Short link copied to clipboard.');
  } catch {
    const temporary = document.createElement('textarea');
    temporary.value = value;
    temporary.style.position = 'fixed';
    temporary.style.opacity = '0';
    document.body.appendChild(temporary);
    temporary.select();
    const copied = document.execCommand('copy');
    temporary.remove();
    showToast(copied ? 'Short link copied to clipboard.' : 'Could not copy the link.', !copied);
  }
}

async function deactivateLink(link) {
  if (!link) return;
  const confirmed = window.confirm(`Disable /${link.shortCode}? Visitors will no longer be redirected.`);
  if (!confirmed) return;

  try {
    await api(`/api/v1/urls/${link.id}`, { method: 'DELETE' });
    await loadLinks();
    showToast('Link disabled.');
  } catch (error) {
    showToast(error.message || 'Could not disable the link.', true);
  }
}

async function showAnalytics(link) {
  if (!link) return;
  elements.analyticsTitle.textContent = `/${link.shortCode}`;
  elements.analyticsSubtitle.textContent = link.originalUrl;
  elements.analyticsTotal.textContent = '—';
  elements.analyticsPeriod.textContent = 'Last 7 days';
  elements.dailyChart.innerHTML = '<p class="field-hint">Loading analytics…</p>';
  elements.analyticsDialog.showModal();

  try {
    const result = await api(`/api/v1/urls/${link.id}/analytics?days=7`);
    elements.analyticsTotal.textContent = formatNumber(result.totalClicks);
    elements.analyticsPeriod.textContent = `Last ${result.periodDays || 7} days`;
    renderDailyChart(result.dailyClicks || []);
  } catch (error) {
    elements.dailyChart.innerHTML = `<p class="field-hint">${escapeHtml(error.message || 'Could not load analytics.')}</p>`;
  }
}

function renderDailyChart(days) {
  if (!days.length) {
    elements.dailyChart.innerHTML = '<p class="field-hint">No click activity for this period.</p>';
    return;
  }
  const maximum = Math.max(1, ...days.map(day => Number(day.clicks) || 0));
  elements.dailyChart.innerHTML = days.map(day => {
    const clicks = Number(day.clicks) || 0;
    const height = clicks === 0 ? 2 : Math.max(7, (clicks / maximum) * 94);
    const date = String(day.date || '');
    const dayLabel = date.length >= 10 ? date.slice(5).replace('-', '/') : date;
    return `
      <div class="chart-day" title="${escapeHtml(date)}: ${clicks} clicks">
        <span class="chart-count">${formatNumber(clicks)}</span>
        <div class="chart-bar-area"><div class="chart-bar" style="height:${height}px"></div></div>
        <span class="chart-date">${escapeHtml(dayLabel)}</span>
      </div>`;
  }).join('');
}

async function handleTableAction(event) {
  const button = event.target.closest('button[data-action]');
  if (!button) return;
  const id = Number(button.dataset.id);
  const link = state.links.find(item => Number(item.id) === id);
  if (!link) return;

  if (button.dataset.action === 'copy') await copyLink(link);
  if (button.dataset.action === 'analytics') await showAnalytics(link);
  if (button.dataset.action === 'deactivate') await deactivateLink(link);
}

async function bootstrap() {
  if (!state.token) {
    showAuth();
    return;
  }

  try {
    state.user = await api('/api/v1/auth/me');
    showDashboard();
    await loadLinks();
  } catch (error) {
    clearSession();
    showAuth();
    if (error.status !== 401) showToast(error.message || 'Could not connect to LinkPulse.', true);
  }
}

elements.loginTab.addEventListener('click', () => setAuthMode('login'));
elements.registerTab.addEventListener('click', () => setAuthMode('register'));
elements.loginForm.addEventListener('submit', event => {
  event.preventDefault();
  authenticate('login', elements.loginForm);
});
elements.registerForm.addEventListener('submit', event => {
  event.preventDefault();
  authenticate('register', elements.registerForm);
});
elements.createForm.addEventListener('submit', createLink);
elements.linksTableBody.addEventListener('click', handleTableAction);
elements.refreshButton.addEventListener('click', loadLinks);
elements.previousPage.addEventListener('click', async () => {
  if (state.page > 0) {
    state.page -= 1;
    await loadLinks();
  }
});
elements.nextPage.addEventListener('click', async () => {
  if (state.page + 1 < state.totalPages) {
    state.page += 1;
    await loadLinks();
  }
});
elements.linkSearch.addEventListener('input', renderLinks);
elements.logoutButton.addEventListener('click', () => {
  clearSession();
  showAuth();
  showToast('You have signed out.');
});
document.getElementById('closeAnalytics').addEventListener('click', () => elements.analyticsDialog.close());
document.getElementById('closeAnalyticsFooter').addEventListener('click', () => elements.analyticsDialog.close());
elements.analyticsDialog.addEventListener('click', event => {
  if (event.target === elements.analyticsDialog) elements.analyticsDialog.close();
});

bootstrap();
