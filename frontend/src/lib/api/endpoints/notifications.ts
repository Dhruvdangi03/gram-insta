import { apiFetch, buildQuery } from '../client'
import type { CursorPage, Notification } from '../types'

export function getNotifications(cursor?: string, limit?: number) {
  return apiFetch<CursorPage<Notification>>(`/notifications${buildQuery({ cursor, limit })}`)
}

export function markNotificationRead(id: number) {
  return apiFetch<void>(`/notifications/${id}/read`, { method: 'PATCH' })
}
