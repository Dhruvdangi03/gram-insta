import { useState } from 'react'
import { Link, Outlet, useParams } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Icon } from '@/components/Icon'
import * as messagingApi from '@/lib/api/endpoints/messaging'
import type { Conversation } from '@/lib/api/types'
import { formatRelativeTime } from '@/lib/formatters/relativeTime'
import { useCursorInfiniteQuery } from '@/lib/hooks/useCursorInfiniteQuery'
import { queryKeys } from '@/lib/queryKeys'
import { NewMessageModal } from './NewMessageModal'
import { useOtherParticipants } from './useOtherParticipants'
import styles from './DirectInboxPage.module.css'

function ConversationRow({ conversation, active }: { conversation: Conversation; active: boolean }) {
  const others = useOtherParticipants(conversation)
  const names = others.map((p) => p.username).join(', ') || 'You'

  return (
    <Link
      to={`/direct/inbox/${conversation.id}`}
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
  const { items, isLoading } = useCursorInfiniteQuery(queryKeys.conversations(), messagingApi.getConversations)

  return (
    <div className={styles.page}>
      <div className={[styles.list, conversationId ? styles.listHiddenOnMobile : ''].join(' ')}>
        <div className={styles.header}>
          <span className={styles.title}>Messages</span>
          <button type="button" className={styles.newMessageButton} onClick={() => setNewMessageOpen(true)} aria-label="New message">
            <Icon name="create" />
          </button>
        </div>
        <div className={styles.items}>
          {!isLoading && items.length === 0 ? (
            <p className={styles.empty}>No messages yet. Start a conversation.</p>
          ) : (
            items.map((conversation) => (
              <ConversationRow
                key={conversation.id}
                conversation={conversation}
                active={String(conversation.id) === conversationId}
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
