import { apiFetch, buildQuery } from '../client'
import type { CursorPage, Story } from '../types'

export interface CreateStoryRequest {
  mediaUrl: string
  expiresInSeconds?: number
}

export function createStory(body: CreateStoryRequest) {
  return apiFetch<Story>('/stories', { method: 'POST', body })
}

export function getUserStories(username: string) {
  return apiFetch<Story[]>(`/users/${encodeURIComponent(username)}/stories`)
}

export function getStoriesFeed(cursor?: string, limit?: number) {
  return apiFetch<CursorPage<Story>>(`/stories/feed${buildQuery({ cursor, limit })}`)
}

export function deleteStory(id: number) {
  return apiFetch<void>(`/stories/${id}`, { method: 'DELETE' })
}

export function markStoryViewed(id: number) {
  return apiFetch<void>(`/stories/${id}/view`, { method: 'POST' })
}
