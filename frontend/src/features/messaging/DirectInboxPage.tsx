import { useState } from 'react'
import { Link, Outlet, useLocation, useParams } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Icon } from '@/components/Icon'
import * as messagingApi from '@/lib/api/endpoints/messaging'
import type { Conversation } from '@/lib/api/types'
import { formatRelativeTime } from '@/lib/formatters/relativeTime'
import { useCursorInfiniteQuery } from '@/lib/hooks/useCursorInfiniteQuery'
import { queryKeys } from '@/lib/queryKeys'
import { useMessageRequestCount } from './useMessageRequestCount'
import { NewMessageModal } from './NewMessageModal'
import { useOtherParticipants } from './useOtherParticipants'
import styles from './DirectInboxPage.module.css'

function ConversationRow({
  conversation,
  active,
  basePath,
}: {
  conversation: Conversation
  active: boolean
  basePath: string
}) {
  const others = useOtherParticipants(conversation)
  const names = others.map((p) => p.username).join(', ') || 'You'

  return (
    <Link
      to={`${basePath}/${conversation.id}`}
      className={[styles.row, active ? styles.rowActive : ''].join(' ')}
    >
      <Avatar src={others[0]?.profilePictureUrl} alt={names} size={44} />
      <div className={styles.rowText}>
        <p className={styles.participants}>{names}</p>
        <p className={styles.timestamp}>{formatRelativeTime(conversation.createdAt)}</p>
      </div>
    </Link>
  )
}

export function DirectInboxPage() {
  const { conversationId } = useParams<{ conversationId: string }>()
  const [newMessageOpen, setNewMessageOpen] = useState(false)
  const isRequests = useLocation().pathname.startsWith('/direct/requests')
  const basePath = isRequests ? '/direct/requests' : '/direct/inbox'
  const requestCount = useMessageRequestCount()
  // Both folders are always mounted so switching tabs is instant; only the active one renders.
  const inbox = useCursorInfiniteQuery(queryKeys.conversations(), messagingApi.getConversations)
  const requests = useCursorInfiniteQuery(queryKeys.messageRequests(), messagingApi.getMessageRequests)
  const { items, isLoading } = isRequests ? requests : inbox

  return (
    <div className={styles.page}>
      <div className={[styles.list, conversationId ? styles.listHiddenOnMobile : ''].join(' ')}>
        <div className={styles.header}>
          <span className={styles.title}>Messages</span>
          <button type="button" className={styles.newMessageButton} onClick={() => setNewMessageOpen(true)} aria-label="New message">
            <Icon name="create" />
          </button>
        </div>
        <div className={styles.tabs} role="tablist">
          <Link to="/direct/inbox" role="tab" aria-selected={!isRequests} className={[styles.tab, !isRequests ? styles.tabActive : ''].join(' ')}>
            Primary
          </Link>
          <Link to="/direct/requests" role="tab" aria-selected={isRequests} className={[styles.tab, isRequests ? styles.tabActive : ''].join(' ')}>
            Requests{requestCount > 0 ? ` (${requestCount})` : ''}
          </Link>
        </div>
        <div className={styles.items}>
          {!isLoading && items.length === 0 ? (
            <p className={styles.empty}>
              {isRequests ? 'No message requests.' : 'No messages yet. Start a conversation.'}
            </p>
          ) : (
            items.map((conversation) => (
              <ConversationRow
                key={conversation.id}
                conversation={conversation}
                active={String(conversation.id) === conversationId}
                basePath={basePath}
              />
            ))
          )}
        </div>
      </div>
      {conversationId ? (
        <Outlet />
      ) : (
        <div className={styles.detail}>
          <p>Select a conversation or start a new one.</p>
        </div>
      )}
      {newMessageOpen ? <NewMessageModal onClose={() => setNewMessageOpen(false)} /> : null}
    </div>
  )
}
