import { apiFetch } from '../client'

export interface CreateReportRequest {
  targetType: 'POST' | 'USER'
  targetId: number
  reason?: string
}

export function report(body: CreateReportRequest) {
  return apiFetch<void>('/reports', { method: 'POST', body })
}
