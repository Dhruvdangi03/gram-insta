import { apiFetch, buildQuery } from '../client'
import type { CursorPage, Post } from '../types'

export function getPostsByHashtag(tag: string, cursor?: string, limit?: number) {
  return apiFetch<CursorPage<Post>>(`/hashtags/${encodeURIComponent(tag)}/posts${buildQuery({ cursor, limit })}`)
}
