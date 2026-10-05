import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import * as authApi from '@/lib/api/endpoints/auth'
import { ensureFreshSession, onAuthExpired, setAccessToken } from '@/lib/api/client'
import type { UserSummary } from '@/lib/api/types'
import { connectStomp, disconnectStomp } from '@/lib/ws/stompClient'
import { AuthContext, type AuthContextValue, type AuthStatus } from './authContextDefinition'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [user, setUser] = useState<UserSummary | null>(null)
  const queryClient = useQueryClient()

  // One place reacting to `status`, rather than calling connect/disconnect at every login/register/
  // logout/bootstrap call site — a call site forgetting to do so was exactly the kind of bug a past
  // review of this codebase's backend flagged ("caller must remember to call N things").
  useEffect(() => {
    if (status === 'authenticated') {
      connectStomp(queryClient)
    } else if (status === 'anonymous') {
      disconnectStomp()
    }
  }, [status, queryClient])

  useEffect(() => {
    // There is no GET /users/me — calling /auth/refresh is the only way to learn whether the
    // httpOnly refresh cookie still represents a valid session on a fresh page load. Goes through
    // the shared single-flight helper (not authApi.refresh() directly) so this and any concurrent
    // 401-triggered refresh (e.g. StrictMode's double effect invocation in dev) share one call
    // instead of racing two against the backend's single-use rotating refresh token.
    let cancelled = false
    ensureFreshSession()
      .then((session) => {
        if (cancelled) return
        setUser(session.user)
        setStatus('authenticated')
      })
      .catch(() => {
        if (cancelled) return
        setStatus('anonymous')
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => onAuthExpired(() => {
    setUser(null)
    setStatus('anonymous')
  }), [])

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      async login(usernameOrEmail, password) {
        const tokens = await authApi.login({ usernameOrEmail, password })
        setAccessToken(tokens.accessToken)
        setUser(tokens.user)
        setStatus('authenticated')
      },
      async register(username, email, password, fullName) {
        const tokens = await authApi.register({ username, email, password, fullName })
        setAccessToken(tokens.accessToken)
        setUser(tokens.user)
        setStatus('authenticated')
      },
      async logout() {
        await authApi.logout().catch(() => {
          // Revocation failing server-side shouldn't strand the user in a logged-in-looking UI.
        })
        setAccessToken(null)
        setUser(null)
        setStatus('anonymous')
        // Several query keys (notifications, conversations, feed, insights, ...) aren't namespaced
        // by user id — without this, a second account logging in on the same tab could render the
        // previous user's cached data as fresh until it naturally goes stale.
        queryClient.clear()
      },
      updateUser(patch) {
        setUser((current) => (current ? { ...current, ...patch } : current))
      },
    }),
    [status, user, queryClient],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
