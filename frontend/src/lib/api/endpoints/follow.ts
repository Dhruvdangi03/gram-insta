import { apiFetch } from '../client'
import type { FollowStatusResponse } from '../types'

export function follow(username: string) {
  return apiFetch<FollowStatusResponse>(`/users/${encodeURIComponent(username)}/follow`, { method: 'POST' })
}

export function unfollow(username: string) {
  return apiFetch<void>(`/users/${encodeURIComponent(username)}/follow`, { method: 'DELETE' })
}

export function acceptFollowRequest(username: string) {
  return apiFetch<FollowStatusResponse>(`/users/${encodeURIComponent(username)}/follow/accept`, { method: 'POST' })
}

export function rejectFollowRequest(username: string) {
  return apiFetch<void>(`/users/${encodeURIComponent(username)}/follow/reject`, { method: 'DELETE' })
}

/** Removes `username` as one of the caller's followers (the reverse of `unfollow`, which is the caller leaving `username`). */
export function removeFollower(username: string) {
  return apiFetch<void>(`/users/${encodeURIComponent(username)}/follow/remove`, { method: 'DELETE' })
}
