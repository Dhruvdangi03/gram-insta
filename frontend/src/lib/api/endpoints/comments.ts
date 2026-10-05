import { apiFetch, buildQuery } from '../client'
import type { Comment, CursorPage } from '../types'

export interface CreateCommentRequest {
  text: string
  parentCommentId?: number
}

export function createComment(postId: number, body: CreateCommentRequest) {
  return apiFetch<Comment>(`/posts/${postId}/comments`, { method: 'POST', body })
}

export function getComments(postId: number, cursor?: string, limit?: number) {
  return apiFetch<CursorPage<Comment>>(`/posts/${postId}/comments${buildQuery({ cursor, limit })}`)
}

export function deleteComment(id: number) {
  return apiFetch<void>(`/comments/${id}`, { method: 'DELETE' })
}
