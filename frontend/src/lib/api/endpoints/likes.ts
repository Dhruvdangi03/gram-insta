import { apiFetch } from '../client'
import type { LikeCountResponse } from '../types'

export function like(postId: number) {
  return apiFetch<LikeCountResponse>(`/posts/${postId}/likes`, { method: 'POST' })
}

export function unlike(postId: number) {
  return apiFetch<LikeCountResponse>(`/posts/${postId}/likes`, { method: 'DELETE' })
}

export function likeComment(commentId: number) {
  return apiFetch<LikeCountResponse>(`/comments/${commentId}/likes`, { method: 'POST' })
}

export function unlikeComment(commentId: number) {
  return apiFetch<LikeCountResponse>(`/comments/${commentId}/likes`, { method: 'DELETE' })
}
