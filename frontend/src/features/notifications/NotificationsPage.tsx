import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import * as notificationsApi from '@/lib/api/endpoints/notifications'
import type { Notification } from '@/lib/api/types'
import { formatRelativeTime } from '@/lib/formatters/relativeTime'
import { useInfiniteScrollSentinel } from '@/lib/hooks/useInfiniteScrollSentinel'
import { queryKeys } from '@/lib/queryKeys'
import { useNotifications } from './useNotifications'
import styles from './NotificationsPage.module.css'

function describe(notification: Notification): string {
  switch (notification.type) {
    case 'LIKE':
      return 'liked your photo.'
    case 'COMMENT':
      return 'commented on your photo.'
    case 'FOLLOW':
      return 'started following you.'
    case 'FOLLOW_REQUEST':
      return 'requested to follow you.'
    case 'FOLLOW_REQUEST_ACCEPTED':
      return 'accepted your follow request.'
  }
}

function targetHref(notification: Notification): string {
  if (notification.type === 'FOLLOW_REQUEST') return '/accounts/follow-requests'
  if (notification.type === 'FOLLOW' || notification.type === 'FOLLOW_REQUEST_ACCEPTED') {
    return `/${notification.actor.username}`
  }
  return `/p/${notification.targetId}`
}

export function NotificationsPage() {
  const { items, isLoading, hasNextPage, fetchNextPage, isFetchingNextPage } = useNotifications()
  const queryClient = useQueryClient()
  const sentinelRef = useInfiniteScrollSentinel(() => fetchNextPage(), Boolean(hasNextPage) && !isFetchingNextPage)

  const markRead = useMutation({
    mutationFn: (id: number) => notificationsApi.markNotificationRead(id),
    onMutate: (id) => {
      queryClient.setQueriesData<{ pages: { items: Notification[] }[] } | undefined>(
        { queryKey: queryKeys.notifications() },
        (data) => {
          if (!data) return data
          return {
            ...data,
            pages: data.pages.map((page) => ({
              ...page,
              items: page.items.map((n) => (n.id === id ? { ...n, read: true } : n)),
            })),
          }
        },
      )
    },
  })

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Notifications</h1>
      {!isLoading && items.length === 0 ? <p className={styles.empty}>No notifications yet.</p> : null}
      {items.map((notification) => (
        <Link
          key={notification.id}
          to={targetHref(notification)}
          className={[styles.row, notification.read ? '' : styles.unread].join(' ')}
          onClick={() => !notification.read && markRead.mutate(notification.id)}
        >
          <Avatar src={notification.actor.profilePictureUrl} alt={notification.actor.username} size={44} />
          <p className={styles.text}>
            <span className={styles.username}>{notification.actor.username}</span> {describe(notification)}{' '}
            <span className={styles.timestamp}>{formatRelativeTime(notification.createdAt)}</span>
          </p>
        </Link>
      ))}
      <div ref={sentinelRef} className={styles.sentinel} />
    </div>
  )
}
