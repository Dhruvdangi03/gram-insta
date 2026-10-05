import { apiFetch, buildQuery } from '../client'
import type { CursorPage, Post } from '../types'

export function getHomeFeed(cursor?: string, limit?: number) {
  return apiFetch<CursorPage<Post>>(`/feed${buildQuery({ cursor, limit })}`)
}

export function getExploreFeed(cursor?: string, limit?: number) {
  return apiFetch<CursorPage<Post>>(`/explore${buildQuery({ cursor, limit })}`)
}
