interface AppIconProps {
  size?: number
  className?: string
}

/**
 * An original homage to the retro, skeuomorphic camera look of early-2010s photo-sharing apps —
 * warm leather body, round chrome-ringed lens, rainbow accent stripe. Hand-drawn from scratch, not
 * traced from any existing app's actual icon file (see the Wordmark component for the same
 * trademark-conscious approach applied to the logotype).
 */
export function AppIcon({ size = 64, className }: AppIconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 100 100"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      aria-hidden="true"
    >
      <defs>
        <linearGradient id="appIconBody" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#e2a765" />
          <stop offset="1" stopColor="#8a5a34" />
        </linearGradient>
        <linearGradient id="appIconRainbow" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stopColor="#f2594b" />
          <stop offset="0.25" stopColor="#f7b733" />
          <stop offset="0.5" stopColor="#5ec46a" />
          <stop offset="0.75" stopColor="#3d8bd6" />
          <stop offset="1" stopColor="#8b5fbf" />
        </linearGradient>
        <radialGradient id="appIconLens" cx="0.35" cy="0.35" r="0.75">
          <stop offset="0" stopColor="#bcd9e8" />
          <stop offset="0.5" stopColor="#3d6b85" />
          <stop offset="1" stopColor="#12222b" />
        </radialGradient>
      </defs>

      <rect x="6" y="16" width="88" height="74" rx="14" fill="url(#appIconBody)" stroke="#5c3b20" strokeWidth="2" />
      <rect x="6" y="16" width="88" height="18" rx="14" fill="#6b4527" opacity="0.35" />
      <rect x="16" y="8" width="20" height="12" rx="3" fill="#3a2415" stroke="#5c3b20" strokeWidth="2" />
      <circle cx="80" cy="12" r="6" fill="#e0574c" stroke="#5c3b20" strokeWidth="2" />
      <rect x="14" y="40" width="72" height="6" rx="3" fill="url(#appIconRainbow)" />

      <circle cx="50" cy="63" r="24" fill="#2b1a10" />
      <circle cx="50" cy="63" r="20" fill="#c9c9c9" />
      <circle cx="50" cy="63" r="17" fill="url(#appIconLens)" />
      <circle cx="43" cy="56" r="4" fill="#ffffff" opacity="0.7" />
    </svg>
  )
}
