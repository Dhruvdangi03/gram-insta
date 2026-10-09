import { apiFetch, buildQuery } from '../client'
import type { UserSummary } from '../types'

const userPath = (username: string) => `/users/${encodeURIComponent(username)}`

export function blockUser(username: string) {
  return apiFetch<void>(`${userPath(username)}/block`, { method: 'POST' })
}

export function unblockUser(username: string) {
  return apiFetch<void>(`${userPath(username)}/block`, { method: 'DELETE' })
}

export function muteUser(username: string) {
  return apiFetch<void>(`${userPath(username)}/mute`, { method: 'POST' })
}

export function unmuteUser(username: string) {
  return apiFetch<void>(`${userPath(username)}/mute`, { method: 'DELETE' })
}

export function getBlockedUsers(limit?: number) {
  return apiFetch<UserSummary[]>(`/users/me/blocked${buildQuery({ limit })}`)
}

export function getMutedUsers(limit?: number) {
  return apiFetch<UserSummary[]>(`/users/me/muted${buildQuery({ limit })}`)
}
