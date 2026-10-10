import type { ReactNode } from 'react'
import { useMessageRequestCount } from './useMessageRequestCount'
import styles from './MessagesBadge.module.css'

/** Wraps the Messages nav icon with a count of pending message requests. */
export function MessagesBadge({ children }: { children: ReactNode }) {
  const count = useMessageRequestCount()

  return (
    <span className={styles.wrapper}>
      {children}
      {count > 0 ? (
        <span className={styles.badge} aria-label={`${count} message requests`}>
          {count > 9 ? '9+' : count}
        </span>
      ) : null}
    </span>
  )
}
