import type { ProblemDetail, UserSummary } from './types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL as string

export class ApiError extends Error {
  status: number
  detail: string
  errors?: Record<string, string>

  constructor(problem: ProblemDetail) {
    super(problem.detail)
    this.status = problem.status
    this.detail = problem.detail
    this.errors = problem.errors
  }
}

// Read synchronously by plain fetch code outside React — AuthContext is the only writer, and
// mirrors its own copy into state purely for rendering.
let accessToken: string | null = null

type Listener = () => void
const authExpiredListeners = new Set<Listener>()

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

export function onAuthExpired(cb: Listener) {
  authExpiredListeners.add(cb)
  return (): void => {
    authExpiredListeners.delete(cb)
  }
}

// Paths that must never trigger the refresh-and-retry loop below — retrying a failed login or a
// failed refresh itself would either loop forever or mask the real error (bad credentials, no cookie).
const AUTH_ENDPOINTS = ['/auth/login', '/auth/register', '/auth/refresh']

interface RefreshedSession {
  accessToken: string
  user: UserSummary
}

let refreshPromise: Promise<RefreshedSession> | null = null

// Single-flight: several queries (feed, stories, notifications) can all 401 within the same tick
// once the 15-minute access token lapses, and — just as important — AuthContext's own mount-time
// bootstrap check must go through this too, not call POST /auth/refresh directly. The refresh
// token is single-use/rotating (the backend atomically GETDELs it), so two concurrent calls
// sharing the same cookie is a real race, not just a hypothetical one: React StrictMode's dev-only
// double effect invocation triggers exactly this — one call rotates the cookie and succeeds, the
// other's request already carried the now-consumed value and 401s, and without single-flighting
// both callers whichever .then/.catch settles last silently overwrites the correct session state.
export async function ensureFreshSession(): Promise<RefreshedSession> {
  if (!refreshPromise) {
    refreshPromise = refreshAccessToken().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

async function refreshAccessToken(): Promise<RefreshedSession> {
  const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
    method: 'POST',
    credentials: 'include',
  })
  if (!response.ok) {
    setAccessToken(null)
    authExpiredListeners.forEach((cb) => cb())
    throw new Error('Session expired')
  }
  const body = (await response.json()) as RefreshedSession
  setAccessToken(body.accessToken)
  return body
}

export interface ApiFetchOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  /** Skip the Authorization header even if a token is present (unused today, kept for clarity at call sites). */
  skipAuth?: boolean
}

async function parseErrorBody(response: Response): Promise<ApiError> {
  try {
    const problem = (await response.json()) as ProblemDetail
    return new ApiError(problem)
  } catch {
    return new ApiError({
      type: 'about:blank',
      title: response.statusText,
      status: response.status,
      detail: response.statusText || 'Request failed',
    })
  }
}

async function doFetch(path: string, options: ApiFetchOptions): Promise<Response> {
  const headers = new Headers(options.headers)
  if (options.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (accessToken && !options.skipAuth) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }
  return fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
    credentials: 'include',
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })
}

/** Typed fetch wrapper: attaches the bearer token, retries once on 401 via a single-flight refresh, and throws ApiError on any other failure. `TResponse` is `void` for 204s. */
export async function apiFetch<TResponse>(path: string, options: ApiFetchOptions = {}): Promise<TResponse> {
  let response = await doFetch(path, options)

  const isAuthEndpoint = AUTH_ENDPOINTS.some((p) => path.startsWith(p))
  if (response.status === 401 && !isAuthEndpoint) {
    try {
      await ensureFreshSession()
    } catch {
      throw await parseErrorBody(response)
    }
    response = await doFetch(path, options)
  }

  if (!response.ok) {
    throw await parseErrorBody(response)
  }
  if (response.status === 204) {
    return undefined as TResponse
  }
  return (await response.json()) as TResponse
}

export function buildQuery(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') {
      search.set(key, String(value))
    }
  }
  const query = search.toString()
  return query ? `?${query}` : ''
}
