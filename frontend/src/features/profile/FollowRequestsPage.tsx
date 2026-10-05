import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Icon } from '@/components/Icon'
import { VerifiedBadge } from '@/components/VerifiedBadge'
import * as followApi from '@/lib/api/endpoints/follow'
import * as usersApi from '@/lib/api/endpoints/users'
import type { UserSummary } from '@/lib/api/types'
import { queryKeys } from '@/lib/queryKeys'
import styles from './FollowRequestsPage.module.css'

/** Lists pending incoming follow requests on the viewer's own private account and lets them
 * accept one — previously POST /users/{username}/follow/accept had no UI path to even discover
 * that a request existed. */
export function FollowRequestsPage() {
  const queryClient = useQueryClient()
  const { data: requests, isLoading } = useQuery({
    queryKey: queryKeys.followRequests(),
    queryFn: () => usersApi.getFollowRequests(),
  })

  const acceptMutation = useMutation({
    mutationFn: (username: string) => followApi.acceptFollowRequest(username),
    onSuccess: (_result, username) => {
      queryClient.setQueryData<UserSummary[]>(queryKeys.followRequests(), (current) =>
        current?.filter((u) => u.username !== username),
      )
    },
  })

  const rejectMutation = useMutation({
    mutationFn: (username: string) => followApi.rejectFollowRequest(username),
    onSuccess: (_result, username) => {
      queryClient.setQueryData<UserSummary[]>(queryKeys.followRequests(), (current) =>
        current?.filter((u) => u.username !== username),
      )
    },
  })

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <Link to="/accounts/edit" className={styles.backButton} aria-label="Back">
          <Icon name="back" />
        </Link>
        <h1 className={styles.title}>Follow Requests</h1>
      </header>
      {isLoading ? null : !requests || requests.length === 0 ? (
        <p className={styles.empty}>No pending follow requests.</p>
      ) : (
        <div className={styles.list}>
          {requests.map((user) => (
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
              <button
                type="button"
                className={styles.declineButton}
                onClick={() => rejectMutation.mutate(user.username)}
                disabled={acceptMutation.isPending || rejectMutation.isPending}
              >
                Decline
              </button>
              <button
                type="button"
                className={styles.acceptButton}
                onClick={() => acceptMutation.mutate(user.username)}
                disabled={acceptMutation.isPending || rejectMutation.isPending}
              >
                Accept
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
