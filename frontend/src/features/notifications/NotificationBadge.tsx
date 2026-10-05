import type { ReactNode } from 'react'
import { useNotifications } from './useNotifications'
import styles from './NotificationBadge.module.css'

/** Wraps the heart nav icon with a small unread-count dot, shown when any fetched notification is unread. */
export function NotificationBadge({ children }: { children: ReactNode }) {
  const { items } = useNotifications()
  const hasUnread = items.some((n) => !n.read)

  return (
    <span className={styles.wrapper}>
      {children}
      {hasUnread ? <span className={styles.badge} /> : null}
    </span>
  )
}
