import { apiFetch } from '../client'
import type { StoryHighlight, StoryHighlightDetail } from '../types'

export interface CreateHighlightRequest {
  title: string
  coverUrl?: string
}

export function createHighlight(body: CreateHighlightRequest) {
  return apiFetch<StoryHighlight>('/users/me/highlights', { method: 'POST', body })
}

export function addHighlightItem(highlightId: number, storyId: number) {
  return apiFetch<void>(`/highlights/${highlightId}/items`, { method: 'POST', body: { storyId } })
}

export function getHighlights(username: string) {
  return apiFetch<StoryHighlight[]>(`/users/${encodeURIComponent(username)}/highlights`)
}

export function getHighlightDetail(highlightId: number) {
  return apiFetch<StoryHighlightDetail>(`/highlights/${highlightId}`)
}

export function deleteHighlight(highlightId: number) {
  return apiFetch<void>(`/highlights/${highlightId}`, { method: 'DELETE' })
}
