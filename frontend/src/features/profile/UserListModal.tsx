import { useMutation, useQueryClient, type InfiniteData } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ActionSheet } from '@/components/ActionSheet'
import { Avatar } from '@/components/Avatar'
import { Button } from '@/components/Button'
import { Modal } from '@/components/Modal'
import { VerifiedBadge } from '@/components/VerifiedBadge'
import { useAuth } from '@/contexts/useAuth'
import { ApiError } from '@/lib/api/client'
import * as followApi from '@/lib/api/endpoints/follow'
import * as usersApi from '@/lib/api/endpoints/users'
import type { CursorPage, UserSummary } from '@/lib/api/types'
import { useCursorInfiniteQuery } from '@/lib/hooks/useCursorInfiniteQuery'
import { useInfiniteScrollSentinel } from '@/lib/hooks/useInfiniteScrollSentinel'
import { queryKeys } from '@/lib/queryKeys'
import styles from './UserListModal.module.css'

/**
 * Backs both /:username/followers and /:username/following. Rows are avatar + username + full
 * name, with no per-row Follow button — UserSummary (what these endpoints return) carries no
 * follow-state field, so a button would have nothing real to reflect without an extra lookup per
 * row. The one exception is your OWN followers list, where each row gets a Remove button: that
 * action is only ever valid on someone who follows you, which the list itself already guarantees.
 */
export function UserListModal({ mode }: { mode: 'followers' | 'following' }) {
  const { username } = useParams<{ username: string }>()
  const navigate = useNavigate()
  const { user: me } = useAuth()
  const queryClient = useQueryClient()
  const [confirmingRemoval, setConfirmingRemoval] = useState<string | null>(null)
  const canRemove = mode === 'followers' && Boolean(username) && me?.username === username

  const query = useCursorInfiniteQuery(
    mode === 'followers' ? queryKeys.followers(username ?? '') : queryKeys.following(username ?? ''),
    (cursor) =>
      mode === 'followers' ? usersApi.getFollowers(username!, cursor) : usersApi.getFollowing(username!, cursor),
    { enabled: Boolean(username) },
  )
  const sentinelRef = useInfiniteScrollSentinel(
    () => query.fetchNextPage(),
    Boolean(query.hasNextPage) && !query.isFetchingNextPage,
  )

  function dropFromFollowers(followerUsername: string) {
    queryClient.setQueryData<InfiniteData<CursorPage<UserSummary>>>(queryKeys.followers(username!), (data) =>
      data
        ? {
            ...data,
            pages: data.pages.map((page) => ({
              ...page,
              items: page.items.filter((u) => u.username !== followerUsername),
            })),
          }
        : data,
    )
    // The profile page behind this modal shows the follower count. `exact` matters: the bare profile
    // key is also a prefix of this very followers list's key, so without it the patch above would
    // be immediately followed by a refetch of every loaded page.
    queryClient.invalidateQueries({ queryKey: queryKeys.userProfile(username!), exact: true })
  }

  const removeMutation = useMutation({
    mutationFn: (followerUsername: string) => followApi.removeFollower(followerUsername),
    onSuccess: (_result, followerUsername) => dropFromFollowers(followerUsername),
    onError: (error, followerUsername) => {
      // A 404 means they already stopped following (unfollowed first, or were removed from another
      // tab or device): the list is just stale, and "already gone" is exactly what was asked for.
      if (error instanceof ApiError && error.status === 404) {
        dropFromFollowers(followerUsername)
        return
      }
      window.alert('Something went wrong removing this follower. Please try again.')
    },
  })

  if (!username) return null

  return (
    <>
      <Modal onClose={() => navigate(-1)} contentClassName={styles.content} labelledBy="user-list-title">
        <h2 id="user-list-title" className={styles.title}>
          {mode === 'followers' ? 'Followers' : 'Following'}
        </h2>
        <div className={styles.list}>
          {!query.isLoading && query.items.length === 0 && !query.hasNextPage ? (
            <p className={styles.empty}>{mode === 'followers' ? 'No followers yet.' : 'Not following anyone yet.'}</p>
          ) : (
            query.items.map((user) => (
              <div key={user.id} className={styles.row}>
                <Link to={`/${user.username}`} className={styles.rowLink}>
                  <Avatar src={user.profilePictureUrl} alt={user.username} size={44} />
                  <div className={styles.rowText}>
                    <span className={styles.username}>
                      {user.username}
                      {user.isVerified ? <VerifiedBadge size={12} /> : null}
                    </span>
                    <span className={styles.fullName}>{user.fullName}</span>
                  </div>
                </Link>
                {canRemove ? (
                  <Button
                    variant="secondary"
                    className={styles.removeButton}
                    aria-label={`Remove ${user.username}`}
                    onClick={() => setConfirmingRemoval(user.username)}
                    disabled={removeMutation.isPending}
                  >
                    Remove
                  </Button>
                ) : null}
              </div>
            ))
          )}
          <div ref={sentinelRef} className={styles.sentinel} />
        </div>
      </Modal>
      {confirmingRemoval ? (
        <ActionSheet
          onClose={() => setConfirmingRemoval(null)}
          actions={[
            {
              label: `Remove @${confirmingRemoval}`,
              destructive: true,
              onClick: () => removeMutation.mutate(confirmingRemoval),
            },
          ]}
        />
      ) : null}
    </>
  )
}
