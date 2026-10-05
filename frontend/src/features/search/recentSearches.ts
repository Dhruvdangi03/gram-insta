import type { UserSearchResult } from '@/lib/api/types'

// Per-viewer convenience only (never synced, never read by the server) — wrapped in try/catch since
// localStorage can throw or be unavailable (private browsing, blocked storage). Stores full result
// objects, not just usernames, so the recent-searches list can render an avatar without extra lookups.
const KEY = 'instaclone:recent-searches'
const MAX = 5

export function getRecentSearches(): UserSearchResult[] {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as UserSearchResult[]) : []
  } catch {
    return []
  }
}

export function addRecentSearch(user: UserSearchResult) {
  try {
    const current = getRecentSearches().filter((u) => u.id !== user.id)
    const next = [user, ...current].slice(0, MAX)
    localStorage.setItem(KEY, JSON.stringify(next))
  } catch {
    // Ignore — recent searches are a convenience, not a requirement.
  }
}

export function clearRecentSearches() {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // Ignore.
  }
}
