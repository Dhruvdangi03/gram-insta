import { differenceInDays, differenceInHours, differenceInMinutes, differenceInSeconds, differenceInWeeks } from 'date-fns'

/** Instagram's compact relative-time format: "3s", "5m", "2h", "4d", "3w" — no "ago" suffix. */
export function formatRelativeTime(isoDate: string): string {
  const date = new Date(isoDate)
  const now = new Date()

  const weeks = differenceInWeeks(now, date)
  if (weeks >= 1) return `${weeks}w`

  const days = differenceInDays(now, date)
  if (days >= 1) return `${days}d`

  const hours = differenceInHours(now, date)
  if (hours >= 1) return `${hours}h`

  const minutes = differenceInMinutes(now, date)
  if (minutes >= 1) return `${minutes}m`

  const seconds = differenceInSeconds(now, date)
  return `${Math.max(seconds, 0)}s`
}

/** Instagram's abbreviated count format: 1234 -> "1,234", 12500 -> "12.5K", 2100000 -> "2.1M". */
export function formatCount(count: number): string {
  if (count < 1000) return String(count)
  if (count < 10000) return count.toLocaleString('en-US')
  if (count < 1_000_000) return `${(count / 1000).toFixed(1).replace(/\.0$/, '')}K`
  return `${(count / 1_000_000).toFixed(1).replace(/\.0$/, '')}M`
}
