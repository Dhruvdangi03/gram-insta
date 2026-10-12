import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, Outlet, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ActionSheet } from '@/components/ActionSheet'
import { Avatar } from '@/components/Avatar'
import { Icon } from '@/components/Icon'
import * as messagingApi from '@/lib/api/endpoints/messaging'
import type { Conversation, CursorPage } from '@/lib/api/types'
import { formatRelativeTime } from '@/lib/formatters/relativeTime'
import { useCursorInfiniteQuery } from '@/lib/hooks/useCursorInfiniteQuery'
import { queryKeys } from '@/lib/queryKeys'
import { useMessageRequestCount } from './useMessageRequestCount'
import { NewMessageModal } from './NewMessageModal'
import { useOtherParticipants } from './useOtherParticipants'
import styles from './DirectInboxPage.module.css'

type ConversationsCache = { pages: CursorPage<Conversation>[]; pageParams: unknown[] }

function ConversationRow({
  conversation,
  active,
  basePath,
  canDelete,
}: {
  conversation: Conversation
  active: boolean
  basePath: string
  canDelete: boolean
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const others = useOtherParticipants(conversation)
  const names = others.map((p) => p.username).join(', ') || 'You'
  const canLinkToProfile = others.length === 1

  const [showSheet, setShowSheet] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  const deleteMutation = useMutation({
    mutationFn: () => messagingApi.deleteConversation(conversation.id),
    onSuccess: () => {
      queryClient.setQueryData<ConversationsCache>(queryKeys.conversations(), (data) =>
        data
          ? {
              ...data,
              pages: data.pages.map((page) => ({
                ...page,
                items: page.items.filter((c) => c.id !== conversation.id),
              })),
            }
          : data,
      )
      if (active) navigate('/direct/inbox')
    },
    onError: () => window.alert('Something went wrong deleting this conversation. Please try again.'),
  })

  function goToThread() {
    navigate(`${basePath}/${conversation.id}`)
  }

  const profilePath = `/${others[0]?.username}`

  const avatar = canLinkToProfile ? (
    <Link to={profilePath} className={styles.avatarLink} onClick={(e) => e.stopPropagation()}>
      <Avatar src={others[0]?.profilePictureUrl} alt={names} size={44} />
    </Link>
  ) : (
    <Avatar src={others[0]?.profilePictureUrl} alt={names} size={44} />
  )

  const username = canLinkToProfile ? (
    <Link to={profilePath} className={styles.usernameLink} onClick={(e) => e.stopPropagation()}>
      {names}
    </Link>
  ) : (
    names
  )

  return (
    <div
      className={[styles.row, active ? styles.rowActive : ''].join(' ')}
      role="button"
      tabIndex={0}
      onClick={goToThread}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          goToThread()
        }
      }}
    >
      <div className={styles.rowIdentity}>
        {avatar}
        <div className={styles.rowText}>
          <p className={styles.participants}>{username}</p>
          <p className={styles.timestamp}>{formatRelativeTime(conversation.createdAt)}</p>
        </div>
      </div>
      {canDelete ? (
        <button
          type="button"
          className={styles.optionsButton}
          aria-label="More options"
          onClick={(e) => {
            e.stopPropagation()
            setShowSheet(true)
          }}
        >
          <Icon name="options" />
        </button>
      ) : null}
      {showSheet ? (
        <ActionSheet
          onClose={() => setShowSheet(false)}
          actions={[{ label: 'Delete', onClick: () => setShowDeleteConfirm(true), destructive: true }]}
        />
      ) : null}
      {showDeleteConfirm ? (
        <ActionSheet
          onClose={() => setShowDeleteConfirm(false)}
          title="Delete conversation?"
          message="This conversation will be removed from your inbox. The other person will still see it."
          actions={[
            {
              label: deleteMutation.isPending ? 'Deleting…' : 'Delete',
              onClick: () => deleteMutation.mutate(),
              destructive: true,
            },
          ]}
        />
      ) : null}
    </div>
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
                canDelete={!isRequests}
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
