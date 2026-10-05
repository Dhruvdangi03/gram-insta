import * as notificationsApi from '@/lib/api/endpoints/notifications'
import { useCursorInfiniteQuery } from '@/lib/hooks/useCursorInfiniteQuery'
import { queryKeys } from '@/lib/queryKeys'

/**
 * Shared by the sidebar's unread badge and the full notifications page — both call this same hook
 * so TanStack Query dedupes them into one observer/cache entry instead of two independent fetches.
 */
export function useNotifications() {
  return useCursorInfiniteQuery(queryKeys.notifications(), notificationsApi.getNotifications)
}
