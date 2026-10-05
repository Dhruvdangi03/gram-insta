import { apiFetch, buildQuery } from '../client'
import type { CursorPage, Post } from '../types'

export function save(postId: number) {
  return apiFetch<void>(`/posts/${postId}/save`, { method: 'POST' })
}

export function unsave(postId: number) {
  return apiFetch<void>(`/posts/${postId}/save`, { method: 'DELETE' })
}

export function getSavedPosts(cursor?: string, limit?: number) {
  return apiFetch<CursorPage<Post>>(`/users/me/saved-posts${buildQuery({ cursor, limit })}`)
}
