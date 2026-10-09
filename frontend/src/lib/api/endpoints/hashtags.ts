import { apiFetch, buildQuery } from '../client'
import type { CursorPage, HashtagSummary, Post } from '../types'

const tagPath = (tag: string) => `/hashtags/${encodeURIComponent(tag)}`

export function getHashtag(tag: string) {
  return apiFetch<HashtagSummary>(tagPath(tag))
}

export function getHashtagPosts(tag: string, cursor?: string) {
  return apiFetch<CursorPage<Post>>(`${tagPath(tag)}/posts${buildQuery({ cursor })}`)
}
