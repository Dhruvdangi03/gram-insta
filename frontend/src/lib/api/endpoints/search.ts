import { apiFetch, buildQuery } from '../client'
import type { PostSearchResult, UserSearchResult } from '../types'

/**
 * The backend's `q` param is required — calling it with an empty query throws an unhandled 500
 * server-side (confirmed during Phase 5 planning), so both functions guard against that here
 * rather than relying on every call site to remember.
 */
export function searchUsers(q: string, limit?: number) {
  if (!q.trim()) return Promise.resolve<UserSearchResult[]>([])
  return apiFetch<UserSearchResult[]>(`/search/users${buildQuery({ q, limit })}`)
}

export function searchPosts(q: string, limit?: number) {
  if (!q.trim()) return Promise.resolve<PostSearchResult[]>([])
  return apiFetch<PostSearchResult[]>(`/search/posts${buildQuery({ q, limit })}`)
}
