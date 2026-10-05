import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Modal } from '@/components/Modal'
import * as messagingApi from '@/lib/api/endpoints/messaging'
import * as searchApi from '@/lib/api/endpoints/search'
import { useDebouncedValue } from '@/lib/hooks/useDebouncedValue'
import { queryKeys } from '@/lib/queryKeys'
import styles from './NewMessageModal.module.css'

export function NewMessageModal({ onClose }: { onClose: () => void }) {
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query.trim(), 300)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const usersQuery = useQuery({
    queryKey: queryKeys.searchUsers(debouncedQuery),
    queryFn: () => searchApi.searchUsers(debouncedQuery),
    enabled: debouncedQuery.length > 0,
  })

  const createConversation = useMutation({
    mutationFn: (username: string) => messagingApi.createConversation({ participantUsernames: [username] }),
    onSuccess: (conversation) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.conversations() })
      onClose()
      navigate(`/direct/inbox/${conversation.id}`)
    },
  })

  return (
    <Modal onClose={onClose} contentClassName={styles.content} labelledBy="new-message-title">
      <h2 id="new-message-title" className={styles.title}>
        New message
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
            onClick={() => createConversation.mutate(user.username)}
            disabled={createConversation.isPending}
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
