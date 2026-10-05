import { createContext } from 'react'
import type { UserSummary } from '@/lib/api/types'

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

export interface AuthContextValue {
  status: AuthStatus
  user: UserSummary | null
  login: (usernameOrEmail: string, password: string) => Promise<void>
  register: (username: string, email: string, password: string, fullName?: string) => Promise<void>
  logout: () => Promise<void>
  /** Merges partial fields into the cached user summary — e.g. after an /accounts/edit save, so the sidebar/mobile-bar avatar and name update without needing a full session refresh. */
  updateUser: (patch: Partial<UserSummary>) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
