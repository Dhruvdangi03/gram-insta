import { ICONS, type IconName } from './icons'

export interface IconProps {
  name: IconName
  size?: number
  variant?: 'outline' | 'filled'
  className?: string
  title?: string
}

export function Icon({ name, size = 24, variant = 'outline', className, title }: IconProps) {
  const def = ICONS[name]
  const useFilled = variant === 'filled' && def.filled
  const d = useFilled ? def.filled! : def.outline

  return (
    <svg
      width={size}
      height={size}
      viewBox={def.viewBox ?? '0 0 24 24'}
      fill="none"
      className={className}
      role={title ? 'img' : 'presentation'}
      aria-hidden={title ? undefined : true}
      aria-label={title}
    >
      {title ? <title>{title}</title> : null}
      <path
        d={d}
        fill={useFilled ? 'currentColor' : 'none'}
        stroke="currentColor"
        strokeWidth={def.strokeWidth ?? 1.7}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
