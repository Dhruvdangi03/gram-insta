import { apiFetch, buildQuery } from '../client'
import type { CursorPage, Insights, UserProfile, UserSummary } from '../types'

export interface UpdateProfileRequest {
  fullName?: string
  bio?: string
  profilePictureUrl?: string
  isPrivate?: boolean
  isBusiness?: boolean
}

export function getProfile(username: string) {
  return apiFetch<UserProfile>(`/users/${encodeURIComponent(username)}`)
}

export function updateMyProfile(body: UpdateProfileRequest) {
  return apiFetch<UserProfile>('/users/me', { method: 'PATCH', body })
}

export function getInsights() {
  return apiFetch<Insights>('/users/me/insights')
}

export function getSuggestions(limit?: number) {
  return apiFetch<UserSummary[]>(`/users/suggestions${buildQuery({ limit })}`)
}

export function getFollowRequests(limit?: number) {
  return apiFetch<UserSummary[]>(`/users/me/follow-requests${buildQuery({ limit })}`)
}

export function getFollowers(username: string, cursor?: string, limit?: number) {
  return apiFetch<CursorPage<UserSummary>>(
    `/users/${encodeURIComponent(username)}/followers${buildQuery({ cursor, limit })}`,
  )
}

export function getFollowing(username: string, cursor?: string, limit?: number) {
  return apiFetch<CursorPage<UserSummary>>(
    `/users/${encodeURIComponent(username)}/following${buildQuery({ cursor, limit })}`,
  )
}

export function blockUser(username: string) {
  return apiFetch<void>(`/users/${encodeURIComponent(username)}/block`, { method: 'POST' })
}

export function unblockUser(username: string) {
  return apiFetch<void>(`/users/${encodeURIComponent(username)}/block`, { method: 'DELETE' })
}

export function restrictUser(username: string) {
  return apiFetch<void>(`/users/${encodeURIComponent(username)}/restrict`, { method: 'POST' })
}

export function unrestrictUser(username: string) {
  return apiFetch<void>(`/users/${encodeURIComponent(username)}/restrict`, { method: 'DELETE' })
}
