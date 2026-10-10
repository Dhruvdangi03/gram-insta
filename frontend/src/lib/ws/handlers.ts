import type { Client, IMessage } from '@stomp/stompjs'
import type { InfiniteData, QueryClient } from '@tanstack/react-query'
import type { CursorPage, Message, Notification } from '@/lib/api/types'
import { queryKeys } from '@/lib/queryKeys'

function prependToFirstPage<T>(
  queryClient: QueryClient,
  key: readonly unknown[],
  item: T,
): void {
  queryClient.setQueryData<InfiniteData<CursorPage<T>>>(key, (data) => {
    if (!data || data.pages.length === 0) return data
    const [firstPage, ...rest] = data.pages
    return { ...data, pages: [{ ...firstPage, items: [item, ...firstPage.items] }, ...rest] }
  })
}

/** No separate WS store — incoming pushes are written straight into the Query cache, the single source of truth for both REST-fetched and WS-pushed data. */
export function registerHandlers(stomp: Client, queryClient: QueryClient) {
  stomp.subscribe('/user/queue/notifications', (frame: IMessage) => {
    const notification = JSON.parse(frame.body) as Notification
    prependToFirstPage(queryClient, queryKeys.notifications(), notification)
    // Whatever a follow-related notification implies is now stale elsewhere in the cache would
    // otherwise only refresh on the next focus/remount, so refresh it as it arrives.
    if (notification.type === 'FOLLOW_REQUEST') {
      // The owner's "Follow Requests (N)" count.
      queryClient.invalidateQueries({ queryKey: queryKeys.followRequests() })
    } else if (notification.type === 'FOLLOW_REQUEST_ACCEPTED') {
      // The actor is the account that accepted: the requester's cached view of it still says
      // "Requested", and its posts and stories now belong in their feed.
      queryClient.invalidateQueries({ queryKey: queryKeys.userProfile(notification.actor.username), exact: true })
      queryClient.invalidateQueries({ queryKey: queryKeys.feed() })
      queryClient.invalidateQueries({ queryKey: queryKeys.storiesFeed() })
    } else if (notification.type === 'FOLLOW') {
      // The recipient's own follower count — there's no user in scope here to target their profile
      // key precisely, and only user queries currently on screen refetch.
      queryClient.invalidateQueries({ queryKey: queryKeys.users() })
    }
  })

  // The server deleted a notification (a request was cancelled/declined/accepted/blocked) — nothing
  // to patch, so refetch the list, the unread dot derived from it, and the request count.
  stomp.subscribe('/user/queue/notifications-changed', () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.notifications() })
    queryClient.invalidateQueries({ queryKey: queryKeys.followRequests() })
  })

  stomp.subscribe('/user/queue/messages', (frame: IMessage) => {
    const message = JSON.parse(frame.body) as Message
    prependToFirstPage(queryClient, queryKeys.messages(message.conversationId), message)
    queryClient.invalidateQueries({ queryKey: queryKeys.conversations() })
    queryClient.invalidateQueries({ queryKey: queryKeys.messageRequests() })
  })

  // A conversation was created, accepted or declined (message requests) — refetch both folders
  // and the Requests badge count.
  stomp.subscribe('/user/queue/conversations-changed', () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.conversations() })
    queryClient.invalidateQueries({ queryKey: queryKeys.messageRequests() })
  })

  stomp.subscribe('/user/queue/errors', (frame: IMessage) => {
    console.error('STOMP error', frame.body)
  })
}
