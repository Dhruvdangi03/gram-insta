import { apiFetch, buildQuery } from '../client'
import type { CursorPage, Post } from '../types'

export interface CreateReelRequest {
  caption?: string
  location?: string
  media: { url: string }
}

export function createReel(body: CreateReelRequest) {
  return apiFetch<Post>('/reels', { method: 'POST', body })
}

export function getReelsFeed(cursor?: string, limit?: number) {
  return apiFetch<CursorPage<Post>>(`/reels/feed${buildQuery({ cursor, limit })}`)
}
