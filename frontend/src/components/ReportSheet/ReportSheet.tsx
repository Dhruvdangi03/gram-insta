import { ActionSheet } from '@/components/ActionSheet'
import * as reportsApi from '@/lib/api/endpoints/reports'
import type { ReportReason, ReportTarget } from '@/lib/api/endpoints/reports'

const REASONS: { reason: ReportReason; label: string }[] = [
  { reason: 'SPAM', label: "It's spam" },
  { reason: 'HARASSMENT', label: 'Bullying or harassment' },
  { reason: 'HATE', label: 'Hate speech or symbols' },
  { reason: 'VIOLENCE', label: 'Violence or dangerous content' },
  { reason: 'NUDITY', label: 'Nudity or sexual content' },
  { reason: 'SCAM', label: 'Scam or fraud' },
  { reason: 'SELF_HARM', label: 'Self-harm' },
  { reason: 'OTHER', label: 'Something else' },
]

/** "Why are you reporting this?" picker, shared by posts, comments and profiles. Picking a reason
 * submits immediately — ActionSheet closes itself on click, so the request is fired directly here
 * rather than through a mutation hook that would be unmounted along with the sheet. */
export function ReportSheet({ target, onClose }: { target: ReportTarget; onClose: () => void }) {
  const submit = (reason: ReportReason) => {
    reportsApi
      .submitReport(target, reason)
      .then(() => window.alert("Thanks for letting us know. We'll review this report."))
      .catch(() => window.alert('Something went wrong sending your report. Please try again.'))
  }

  return (
    <ActionSheet
      onClose={onClose}
      title="Report"
      message="Why are you reporting this?"
      actions={REASONS.map(({ reason, label }) => ({ key: reason, label, onClick: () => submit(reason) }))}
    />
  )
}
