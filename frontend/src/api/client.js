const resolveApiBase = () => {
  const envUrl = import.meta.env.VITE_API_URL;
  if (!envUrl) return '/api';
  let trimmed = envUrl.trim().replace(/\/+$/, '');
  if (!trimmed.startsWith('http://') && !trimmed.startsWith('https://') && !trimmed.startsWith('/')) {
    trimmed = `https://${trimmed}`;
  }
  return trimmed.endsWith('/api') ? trimmed : `${trimmed}/api`;
};

const API_BASE = resolveApiBase();

const TOKEN_KEY = 'zoner_access_token';
const REFRESH_KEY = 'zoner_refresh_token';

let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

/**
 * Storage helpers
 */
export const tokenStorage = {
  getAccessToken: () => localStorage.getItem(TOKEN_KEY),
  getRefreshToken: () => localStorage.getItem(REFRESH_KEY),
  setTokens: (accessToken, refreshToken) => {
    if (accessToken) localStorage.setItem(TOKEN_KEY, accessToken);
    if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken);
  },
  clearTokens: () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

/**
 * Generate lightweight UUID for request correlation tracking
 */
function generateCorrelationId() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/**
 * Core fetch wrapper with auth header injection and automatic token refresh
 */
export async function apiRequest(endpoint, options = {}) {
  const url = `${API_BASE}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;
  const headers = {
    'Content-Type': 'application/json',
    'X-Correlation-Id': generateCorrelationId(),
    ...options.headers,
  };

  const accessToken = tokenStorage.getAccessToken();
  if (accessToken && !headers.Authorization) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  const config = {
    ...options,
    headers,
  };

  try {
    const response = await fetch(url, config);

    // If 401 Unauthorized and not already calling refresh or login
    if (response.status === 401 && !endpoint.includes('/auth/')) {
      const refreshToken = tokenStorage.getRefreshToken();
      if (!refreshToken) {
        tokenStorage.clearTokens();
        window.dispatchEvent(new CustomEvent('zoner:auth-expired'));
        throw await response.json().catch(() => ({ code: 'UNAUTHORIZED', message: 'Session expired' }));
      }

      if (isRefreshing) {
        // Queue this request until refresh finishes
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then((newAccessToken) => {
          config.headers.Authorization = `Bearer ${newAccessToken}`;
          return fetch(url, config).then((res) => handleResponse(res));
        });
      }

      isRefreshing = true;

      try {
        const refreshRes = await fetch(`${API_BASE}/auth/refresh`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Correlation-Id': generateCorrelationId(),
          },
          body: JSON.stringify({ refreshToken }),
        });

        if (!refreshRes.ok) {
          throw new Error('Refresh failed');
        }

        const data = await refreshRes.json();
        tokenStorage.setTokens(data.accessToken, data.refreshToken);
        processQueue(null, data.accessToken);
        isRefreshing = false;

        // Replay original request
        config.headers.Authorization = `Bearer ${data.accessToken}`;
        const replayedResponse = await fetch(url, config);
        return handleResponse(replayedResponse);
      } catch (refreshErr) {
        processQueue(refreshErr, null);
        isRefreshing = false;
        tokenStorage.clearTokens();
        window.dispatchEvent(new CustomEvent('zoner:auth-expired'));
        throw { code: 'UNAUTHORIZED', message: 'Session expired. Please log in again.' };
      }
    }

    return handleResponse(response);
  } catch (err) {
    if (err.code) throw err;
    throw {
      code: 'NETWORK_ERROR',
      message: err.message || 'Unable to connect to the server. Please check your connection.',
    };
  }
}

async function handleResponse(response) {
  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get('content-type');
  const isJson = contentType && contentType.includes('application/json');
  const data = isJson ? await response.json() : await response.text();

  if (!response.ok) {
    // If structured ApiError from backend
    if (typeof data === 'object' && data !== null) {
      throw data;
    }
    throw {
      code: `HTTP_${response.status}`,
      message: data || response.statusText || 'An error occurred',
    };
  }

  return data;
}

/**
 * Standard API Client methods
 */
export const api = {
  // Authentication
  auth: {
    register: (body) => apiRequest('/auth/register', { method: 'POST', body: JSON.stringify(body) }),
    login: (credentials) => apiRequest('/auth/login', { method: 'POST', body: JSON.stringify(credentials) }),
    refresh: (refreshToken) => apiRequest('/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken }) }),
    logout: (refreshToken) => apiRequest('/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken }) }),
  },

  // User Profile
  user: {
    getMe: () => apiRequest('/me'),
    updateMe: (profile) => apiRequest('/me', { method: 'PATCH', body: JSON.stringify(profile) }),
  },

  // Calendars
  calendars: {
    list: async () => {
      const data = await apiRequest('/calendars');
      return (data || []).map((c) => ({
        ...c,
        isOwner: c.permission === 'OWNER',
      }));
    },
    create: (calendar) => apiRequest('/calendars', { method: 'POST', body: JSON.stringify(calendar) }),
    get: (id) => apiRequest(`/calendars/${id}`),
    update: (id, calendar) => apiRequest(`/calendars/${id}`, { method: 'PUT', body: JSON.stringify(calendar) }),
    delete: (id) => apiRequest(`/calendars/${id}`, { method: 'DELETE' }),
    updatePreferences: (id, prefs) => apiRequest(`/calendars/${id}/preference`, { method: 'PATCH', body: JSON.stringify(prefs) }),
    listShares: (id) => apiRequest(`/calendars/${id}/shares`),
    share: (id, shareData) => apiRequest(`/calendars/${id}/shares`, { method: 'POST', body: JSON.stringify(shareData) }),
    removeShare: (id, userId) => apiRequest(`/calendars/${id}/shares/${userId}`, { method: 'DELETE' }),
  },

  // Events
  events: {
    list: ({ from, to, calendarIds }) => {
      const params = new URLSearchParams();
      if (from) params.append('from', from);
      if (to) params.append('to', to);
      if (calendarIds && calendarIds.length > 0) {
        calendarIds.forEach((id) => params.append('calendarIds', id));
      }
      return apiRequest(`/events?${params.toString()}`);
    },
    create: (event) => apiRequest('/events', { method: 'POST', body: JSON.stringify(event) }),
    get: (id) => apiRequest(`/events/${id}`),
    update: (id, event, editMode = 'ALL', occurrenceStart = null) => {
      const params = new URLSearchParams({ editMode });
      if (occurrenceStart) params.append('occurrenceStart', occurrenceStart);
      return apiRequest(`/events/${id}?${params.toString()}`, { method: 'PUT', body: JSON.stringify(event) });
    },
    delete: (id, editMode = 'ALL', occurrenceStart = null) => {
      const params = new URLSearchParams({ editMode });
      if (occurrenceStart) params.append('occurrenceStart', occurrenceStart);
      return apiRequest(`/events/${id}?${params.toString()}`, { method: 'DELETE' });
    },
    search: (query, from, to) => {
      const params = new URLSearchParams({ q: query, query });
      if (from) params.append('from', from);
      if (to) params.append('to', to);
      return apiRequest(`/events/search?${params.toString()}`);
    },
    checkAvailability: (start, end, excludeEventId = null) => {
      const params = new URLSearchParams({ start, end, from: start, to: end });
      if (excludeEventId) params.append('excludeEventId', excludeEventId);
      return apiRequest(`/events/availability?${params.toString()}`);
    },
    rsvp: (id, status) => apiRequest(`/events/${id}/rsvp`, { method: 'PUT', body: JSON.stringify({ status }) }),
  },

  // Notifications
  notifications: {
    list: (unreadOnly = false) => apiRequest(`/notifications?unreadOnly=${unreadOnly}`),
    getUnreadCount: () => apiRequest('/notifications/unread-count'),
    markAsRead: (id) => apiRequest(`/notifications/${id}/read`, { method: 'PATCH' }),
    markAllAsRead: () => apiRequest('/notifications/mark-all-read', { method: 'POST' }),
    delete: (id) => apiRequest(`/notifications/${id}`, { method: 'DELETE' }),
  },

  // Personal Access Tokens (PATs) for MCP
  tokens: {
    list: () => apiRequest('/tokens'),
    create: (data) => apiRequest('/tokens', { method: 'POST', body: JSON.stringify(data) }),
    revoke: (id) => apiRequest(`/tokens/${id}`, { method: 'DELETE' }),
  },
};
