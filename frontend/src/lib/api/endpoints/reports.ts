import { apiFetch } from '../client'

export type ReportReason = 'SPAM' | 'HARASSMENT' | 'HATE' | 'VIOLENCE' | 'NUDITY' | 'SCAM' | 'SELF_HARM' | 'OTHER'

export type ReportTarget =
  | { type: 'POST'; id: number }
  | { type: 'COMMENT'; id: number }
  | { type: 'USER'; username: string }

export interface ReportResponse {
  id: number
  status: 'OPEN' | 'REVIEWED' | 'DISMISSED'
}

function pathFor(target: ReportTarget) {
  switch (target.type) {
    case 'POST':
      return `/posts/${target.id}/report`
    case 'COMMENT':
      return `/comments/${target.id}/report`
    case 'USER':
      return `/users/${encodeURIComponent(target.username)}/report`
  }
}

export function submitReport(target: ReportTarget, reason: ReportReason, details?: string) {
  return apiFetch<ReportResponse>(pathFor(target), { method: 'POST', body: { reason, details } })
}
