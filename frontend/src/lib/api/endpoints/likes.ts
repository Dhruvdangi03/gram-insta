import { apiFetch } from '../client'
import type { LikeCountResponse } from '../types'

export function like(postId: number) {
  return apiFetch<LikeCountResponse>(`/posts/${postId}/likes`, { method: 'POST' })
}

export function unlike(postId: number) {
  return apiFetch<LikeCountResponse>(`/posts/${postId}/likes`, { method: 'DELETE' })
}
