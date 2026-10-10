import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Icon } from '@/components/Icon'
import { VerifiedBadge } from '@/components/VerifiedBadge'
import * as restrictionsApi from '@/lib/api/endpoints/restrictions'
import type { UserSummary } from '@/lib/api/types'
import { queryKeys } from '@/lib/queryKeys'
import styles from './FollowRequestsPage.module.css'

function UserRows({
  users,
  actionLabel,
  onAction,
  disabled,
}: {
  users: UserSummary[]
  actionLabel: string
  onAction: (username: string) => void
  disabled: boolean
}) {
  return (
    <div className={styles.list}>
      {users.map((user) => (
        <div key={user.id} className={styles.row}>
          <Link to={`/${user.username}`}>
            <Avatar src={user.profilePictureUrl} alt={user.username} size={44} />
          </Link>
          <div className={styles.rowText}>
            <Link to={`/${user.username}`} className={styles.username}>
              {user.username}
              {user.isVerified ? <VerifiedBadge size={12} /> : null}
            </Link>
            {user.fullName ? <span className={styles.fullName}>{user.fullName}</span> : null}
          </div>
          <button type="button" className={styles.acceptButton} onClick={() => onAction(user.username)} disabled={disabled}>
            {actionLabel}
          </button>
        </div>
      ))}
    </div>
  )
}

/** Manage accounts the viewer has blocked or muted. Reuses FollowRequestsPage's styles — same
 * "header + list of people with an action button" shape. */
export function BlockedMutedPage() {
  const queryClient = useQueryClient()
  const blocked = useQuery({ queryKey: queryKeys.blockedUsers(), queryFn: () => restrictionsApi.getBlockedUsers() })
  const muted = useQuery({ queryKey: queryKeys.mutedUsers(), queryFn: () => restrictionsApi.getMutedUsers() })
  const restricted = useQuery({
    queryKey: queryKeys.restrictedUsers(),
    queryFn: () => restrictionsApi.getRestrictedUsers(),
  })

  // Block/mute changes what feeds return, so a plain list update isn't enough.
  const invalidateAfterChange = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.users() })
    queryClient.invalidateQueries({ queryKey: queryKeys.feed() })
    queryClient.invalidateQueries({ queryKey: queryKeys.reelsFeed() })
    queryClient.invalidateQueries({ queryKey: queryKeys.storiesFeed() })
  }
  const unblock = useMutation({ mutationFn: restrictionsApi.unblockUser, onSuccess: invalidateAfterChange })
  const unmute = useMutation({ mutationFn: restrictionsApi.unmuteUser, onSuccess: invalidateAfterChange })
  const unrestrict = useMutation({
    mutationFn: restrictionsApi.unrestrictUser,
    onSuccess: () => {
      invalidateAfterChange()
      queryClient.invalidateQueries({ queryKey: queryKeys.conversations() })
      queryClient.invalidateQueries({ queryKey: queryKeys.messageRequests() })
    },
  })

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <Link to="/accounts/edit" className={styles.backButton} aria-label="Back">
          <Icon name="back" />
        </Link>
        <h1 className={styles.title}>Blocked, muted &amp; restricted</h1>
      </header>

      <h2 className={styles.username}>Blocked accounts</h2>
      {!blocked.data || blocked.data.length === 0 ? (
        <p className={styles.empty}>You haven&apos;t blocked anyone.</p>
      ) : (
        <UserRows
          users={blocked.data}
          actionLabel="Unblock"
          onAction={(u) => unblock.mutate(u)}
          disabled={unblock.isPending}
        />
      )}

      <h2 className={styles.username}>Muted accounts</h2>
      {!muted.data || muted.data.length === 0 ? (
        <p className={styles.empty}>You haven&apos;t muted anyone.</p>
      ) : (
        <UserRows users={muted.data} actionLabel="Unmute" onAction={(u) => unmute.mutate(u)} disabled={unmute.isPending} />
      )}

      <h2 className={styles.username}>Restricted accounts</h2>
      {!restricted.data || restricted.data.length === 0 ? (
        <p className={styles.empty}>You haven&apos;t restricted anyone.</p>
      ) : (
        <UserRows
          users={restricted.data}
          actionLabel="Unrestrict"
          onAction={(u) => unrestrict.mutate(u)}
          disabled={unrestrict.isPending}
        />
      )}
    </div>
  )
}
