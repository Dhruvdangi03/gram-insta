import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, ensureFreshSession, getAccessToken, setAccessToken } from './client'

const SESSION_BODY = {
  accessToken: 'fresh-token',
  tokenType: 'Bearer',
  expiresInSeconds: 900,
  user: { id: 1, username: 'alice', fullName: 'Alice', profilePictureUrl: '' },
}

describe('ensureFreshSession', () => {
  beforeEach(() => {
    setAccessToken(null)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('single-flights concurrent callers into exactly one /auth/refresh request', async () => {
    // Regression test for a real bug: React StrictMode's double effect invocation fired two
    // concurrent /auth/refresh calls on mount, racing against the backend's single-use rotating
    // refresh token — one succeeded and rotated the cookie, the other 401'd on the now-stale
    // value, and whichever handler settled last silently overwrote the correct session state.
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(SESSION_BODY),
    })
    vi.stubGlobal('fetch', fetchMock)

    const [a, b, c] = await Promise.all([ensureFreshSession(), ensureFreshSession(), ensureFreshSession()])

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(a).toEqual(SESSION_BODY)
    expect(b).toEqual(SESSION_BODY)
    expect(c).toEqual(SESSION_BODY)
    expect(getAccessToken()).toBe('fresh-token')
  })

  it('starts a new refresh call after the in-flight one settles', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(SESSION_BODY),
    })
    vi.stubGlobal('fetch', fetchMock)

    await ensureFreshSession()
    await ensureFreshSession()

    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})

describe('apiFetch 401 handling', () => {
  beforeEach(() => {
    setAccessToken('expired-token')
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('refreshes once and retries the original request on a single 401', async () => {
    const fetchMock = vi
      .fn()
      // First attempt at the protected endpoint: 401.
      .mockResolvedValueOnce({ ok: false, status: 401, json: () => Promise.resolve({}) })
      // The refresh call.
      .mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve(SESSION_BODY) })
      // The retried original request succeeds.
      .mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve({ items: [] }) })
    vi.stubGlobal('fetch', fetchMock)

    const result = await apiFetch('/feed')

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(result).toEqual({ items: [] })
    expect(getAccessToken()).toBe('fresh-token')
  })

  it('never retries a 401 from /auth/login itself (would loop on bad credentials)', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      json: () => Promise.resolve({ type: 'about:blank', title: 'Unauthorized', status: 401, detail: 'Bad credentials' }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiFetch('/auth/login', { method: 'POST', body: {} })).rejects.toThrow('Bad credentials')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
