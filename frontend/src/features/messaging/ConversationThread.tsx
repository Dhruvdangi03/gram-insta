import { useMutation } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Icon } from '@/components/Icon'
import { useAuth } from '@/contexts/useAuth'
import * as messagingApi from '@/lib/api/endpoints/messaging'
import { useCursorInfiniteQuery } from '@/lib/hooks/useCursorInfiniteQuery'
import { queryKeys } from '@/lib/queryKeys'
import styles from './ConversationThread.module.css'

export function ConversationThread() {
  const { conversationId } = useParams<{ conversationId: string }>()
  const id = Number(conversationId)
  const { user } = useAuth()
  const [draft, setDraft] = useState('')
  const bottomRef = useRef<HTMLDivElement | null>(null)

  // No GET /conversations/{id} endpoint — the conversation's metadata (participants) comes from
  // the list query DirectInboxPage already fetches; calling the same hook here just dedupes onto
  // that same cached observer instead of re-fetching.
  const { items: conversations } = useCursorInfiniteQuery(queryKeys.conversations(), messagingApi.getConversations)
  const conversation = conversations.find((c) => c.id === id)

  const { items: messages } = useCursorInfiniteQuery(queryKeys.messages(id), (cursor) =>
    messagingApi.getMessages(id, cursor),
  )
  // The backend returns newest-first (matching this app's createdAt DESC convention everywhere
  // else) — a chat thread reads oldest-to-newest top-to-bottom, so reverse for display only.
  const orderedMessages = [...messages].reverse()

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [orderedMessages.length])

  const sendMutation = useMutation({
    mutationFn: (content: string) => messagingApi.sendMessage(id, { content }),
    onSuccess: () => setDraft(''),
  })

  function handleSend() {
    const text = draft.trim()
    if (!text) return
    sendMutation.mutate(text)
  }

  if (!conversation) {
    return <div className={styles.thread} />
  }

  const others = conversation.participants.filter((p) => p.id !== user?.id)
  const names = others.map((p) => p.username).join(', ')

  return (
    <div className={styles.thread}>
      <header className={styles.header}>
        <Link to="/direct/inbox" className={styles.backButton} aria-label="Back to messages">
          <Icon name="back" />
        </Link>
        <Avatar src={others[0]?.profilePictureUrl} alt={names} size={32} />
        <span className={styles.headerName}>{names}</span>
      </header>
      <div className={styles.messages}>
        {orderedMessages.map((message) => {
          const isOwn = message.sender.id === user?.id
          return (
            <div key={message.id} className={[styles.bubbleRow, isOwn ? styles.bubbleRowOwn : ''].join(' ')}>
              {!isOwn ? <Avatar src={message.sender.profilePictureUrl} alt={message.sender.username} size={24} /> : null}
              {message.sharedPost ? (
                <Link
                  to={`/p/${message.sharedPost.id}`}
                  className={[styles.bubble, styles.sharedPostCard, isOwn ? styles.bubbleOwn : ''].join(' ')}
                >
                  <img
                    className={styles.sharedPostThumb}
                    src={message.sharedPost.media[0]?.thumbnailUrl ?? message.sharedPost.media[0]?.url}
                    alt={message.sharedPost.caption || `Post by ${message.sharedPost.author.username}`}
                  />
                  <div className={styles.sharedPostCaption}>
                    <span className={styles.sharedPostAuthor}>{message.sharedPost.author.username}</span>
                    {message.sharedPost.caption ? <span>{message.sharedPost.caption}</span> : null}
                  </div>
                </Link>
              ) : (
                <div className={[styles.bubble, isOwn ? styles.bubbleOwn : ''].join(' ')}>{message.content}</div>
              )}
            </div>
          )
        })}
        <div ref={bottomRef} />
      </div>
      <div className={styles.composer}>
        <input
          className={styles.composerInput}
          placeholder="Message…"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') handleSend()
          }}
          maxLength={1000}
        />
        <button type="button" className={styles.sendButton} disabled={!draft.trim() || sendMutation.isPending} onClick={handleSend}>
          Send
        </button>
      </div>
    </div>
  )
}
