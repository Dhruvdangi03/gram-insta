import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Modal } from '@/components/Modal'
import * as messagingApi from '@/lib/api/endpoints/messaging'
import * as searchApi from '@/lib/api/endpoints/search'
import type { Post } from '@/lib/api/types'
import { useDebouncedValue } from '@/lib/hooks/useDebouncedValue'
import { queryKeys } from '@/lib/queryKeys'
import styles from './SharePostModal.module.css'

export function SharePostModal({ post, onClose }: { post: Post; onClose: () => void }) {
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query.trim(), 300)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const usersQuery = useQuery({
    queryKey: queryKeys.searchUsers(debouncedQuery),
    queryFn: () => searchApi.searchUsers(debouncedQuery),
    enabled: debouncedQuery.length > 0,
  })

  const shareMutation = useMutation({
    mutationFn: async (username: string) => {
      const conversation = await messagingApi.createConversation({ participantUsernames: [username] })
      await messagingApi.sendMessage(conversation.id, { sharedPostId: post.id })
      return conversation
    },
    onSuccess: (conversation) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.conversations() })
      onClose()
      navigate(`/direct/inbox/${conversation.id}`)
    },
  })

  return (
    <Modal onClose={onClose} contentClassName={styles.content} labelledBy="share-post-title">
      <h2 id="share-post-title" className={styles.title}>
        Share
      </h2>
      <div className={styles.searchRow}>
        <input
          className={styles.input}
          placeholder="Search…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          autoFocus
        />
      </div>
      <div className={styles.list}>
        {usersQuery.data?.map((user) => (
          <button
            key={user.id}
            type="button"
            className={styles.row}
            onClick={() => shareMutation.mutate(user.username)}
            disabled={shareMutation.isPending}
          >
            <Avatar src={user.profilePictureUrl} alt={user.username} size={44} />
            <div className={styles.rowText}>
              <span className={styles.username}>{user.username}</span>
              <span className={styles.fullName}>{user.fullName}</span>
            </div>
          </button>
        ))}
      </div>
    </Modal>
  )
}
