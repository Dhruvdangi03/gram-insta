/** A small blue verified badge — original circular seal + checkmark design, not traced from
 * Instagram's own scalloped badge asset (same trademark-conscious approach as the rest of the
 * hand-built icon set, see icons.ts). */
export function VerifiedBadge({ size = 14, className }: { size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" className={className} aria-label="Verified" role="img">
      <circle cx="12" cy="12" r="10" fill="#3897f0" />
      <path
        d="M7.5 12.5 10.3 15.3 16.5 9"
        fill="none"
        stroke="#fff"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
