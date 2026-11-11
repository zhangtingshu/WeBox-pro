// Single API boundary: token in memory only, refresh token stays in an httpOnly cookie.
// 4xx is never retried; 5xx is retried up to 3 times; network failure -> offline message.
const BASE = import.meta.env.VITE_API_BASE || '/api'

let accessToken = null
let onSessionLost = () => {}

export class ApiError extends Error {
  constructor(code, message, status) {
    super(message)
    this.code = code
    this.status = status
  }
}

export function setToken(token) { accessToken = token }
export function getToken() { return accessToken }
export function clearToken() { accessToken = null }
export function onSessionExpired(handler) { onSessionLost = handler }

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

function friendly(message, status) {
  if (!message) return status >= 500
    ? 'The server is temporarily unavailable. Please try again.'
    : 'Something went wrong. Please try again.'
  return message
}

function headersFor(options) {
  const headers = { Accept: 'application/json' }
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`
  if (options.body !== undefined && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
  }
  if (options.headers) Object.assign(headers, options.headers)
  return headers
}

function bodyFor(options) {
  if (options.body === undefined) return undefined
  return options.body instanceof FormData ? options.body : JSON.stringify(options.body)
}

async function refreshSession() {
  try {
    const res = await fetch(`${BASE}/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) return false
    const payload = await res.json()
    if (payload?.data?.accessToken) {
      setToken(payload.data.accessToken)
      return true
    }
    return false
  } catch {
    return false
  }
}

async function rawFetch(path, options) {
  return fetch(`${BASE}${path}`, {
    method: options.method || 'GET',
    headers: headersFor(options),
    body: bodyFor(options),
    credentials: 'include',
    signal: options.signal,
  })
}

/** Standard JSON request. Returns payload.data. */
export async function api(path, options = {}) {
  let skipRefresh = options.skipRefresh === true
  let lastError = null

  for (let attempt = 0; attempt < 4; attempt++) {
    let response
    try {
      response = await rawFetch(path, options)
    } catch (error) {
      if (error?.name === 'AbortError') throw error
      throw new ApiError('OFFLINE',
        'You appear to be offline. Please check your connection and try again.', 0)
    }

    if (response.status === 401 && !skipRefresh) {
      const refreshed = await refreshSession()
      if (refreshed) {
        skipRefresh = true
        attempt = -1
        continue
      }
      clearToken()
      onSessionLost()
      throw new ApiError('UNAUTHORIZED', 'Your session has expired. Please sign in again.', 401)
    }

    if (response.status >= 500 && attempt < 3) {
      lastError = new ApiError('SERVER_ERROR', friendly(null, response.status), response.status)
      await sleep(350 * (attempt + 1))
      continue
    }

    let payload = {}
    try { payload = await response.json() } catch { /* empty body */ }
    if (!response.ok) {
      throw new ApiError(payload?.error?.code || 'ERROR',
        friendly(payload?.error?.message, response.status), response.status)
    }
    return payload.data
  }
  throw lastError || new ApiError('SERVER_ERROR', 'The server is temporarily unavailable.', 500)
}

/**
 * SSE over fetch() (not EventSource) so the Bearer token never appears in a URL.
 * onEvent(eventName, dataString) is called for every server-sent event.
 */
export async function stream(path, { method = 'GET', body, signal, onEvent } = {}) {
  const headers = { Accept: 'text/event-stream' }
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let response = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    credentials: 'include',
    signal,
  })
  if (response.status === 401) {
    const refreshed = await refreshSession()
    if (!refreshed) {
      onSessionLost()
      throw new ApiError('UNAUTHORIZED', 'Your session has expired. Please sign in again.', 401)
    }
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`
    response = await fetch(`${BASE}${path}`, {
      method, headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'include', signal,
    })
  }
  if (!response.ok || !response.body) {
    throw new ApiError('STREAM_ERROR', 'Could not open the live stream. Please try again.', response.status)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let separator = buffer.indexOf('\n\n')
      while (separator >= 0) {
        const block = buffer.slice(0, separator)
        buffer = buffer.slice(separator + 2)
        let event = 'message'
        let data = ''
        for (const line of block.split('\n')) {
          if (line.startsWith('event:')) event = line.slice(6).trim()
          else if (line.startsWith('data:')) data += line.slice(5).trim()
        }
        if (data) onEvent(event, data)
        separator = buffer.indexOf('\n\n')
      }
    }
  } catch (error) {
    if (error?.name !== 'AbortError') throw error
  }
}

export const authApi = {
  register: (body) => api('/auth/register', { method: 'POST', body }),
  login: (body) => api('/auth/login', { method: 'POST', body }),
  logout: () => api('/auth/logout', { method: 'POST' }),
  me: () => api('/auth/me'),
}

export const menuApi = {
  list: (params) => api(`/menu?${new URLSearchParams(params).toString()}`),
  detail: (id) => api(`/dishes/${id}`),
  meta: () => api('/meta'),
  streamStock: (opts) => stream('/menu/stream', opts),
}

export const preferenceApi = {
  get: () => api('/preferences'),
  save: (body) => api('/preferences', { method: 'PUT', body }),
}

export const orderApi = {
  place: (body, key) => api('/orders', {
    method: 'POST', body, headers: { 'Idempotency-Key': key },
  }),
  list: () => api('/orders'),
  detail: (id) => api(`/orders/${id}`),
  cancel: (id) => api(`/orders/${id}/cancel`, { method: 'POST' }),
  active: (date, mealPeriod) => api(`/orders/active?${new URLSearchParams({ date, mealPeriod })}`),
  addresses: () => api('/orders/addresses'),
}

export const aiApi = {
  recommend: (query, opts) => stream('/ai/recommend', {
    method: 'POST', body: { query }, ...opts,
  }),
  status: () => api('/ai/status'),
}

export const consoleApi = {
  dishes: (params) => api(`/console/dishes?${new URLSearchParams(params).toString()}`),
  createDish: (body) => api('/console/dishes', { method: 'POST', body }),
  updateDish: (id, body) => api(`/console/dishes/${id}`, { method: 'PUT', body }),
  setActive: (id, active) => api(`/console/dishes/${id}/active`, { method: 'PATCH', body: { active } }),
  upload: (formData) => api('/console/uploads', { method: 'POST', body: formData }),
  dailyMenu: (date) => api(`/console/daily-menu?date=${encodeURIComponent(date)}`),
  saveDailyMenu: (body) => api('/console/daily-menu', { method: 'PUT', body }),
  dashboard: () => api('/console/dashboard'),
  cacheStats: () => api('/console/cache-stats'),
}
