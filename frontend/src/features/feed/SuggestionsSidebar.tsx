import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { VerifiedBadge } from '@/components/VerifiedBadge'
import { useAuth } from '@/contexts/useAuth'
import * as followApi from '@/lib/api/endpoints/follow'
import * as usersApi from '@/lib/api/endpoints/users'
import type { UserSummary } from '@/lib/api/types'
import { queryKeys } from '@/lib/queryKeys'
import styles from './SuggestionsSidebar.module.css'

/** Desktop-only right rail: mini own-profile card + a short "suggested for you" list, ranked by
 * follower count server-side (see UserService.getSuggestions). A followed suggestion just
 * disappears from the list — there's no per-row profile cache to reconcile against (UserSummary
 * carries no follow-state field), unlike useFollowMutation's full-UserProfile patch. */
export function SuggestionsSidebar() {
  const { user } = useAuth()
  const queryClient = useQueryClient()

  const { data: suggestions } = useQuery({
    queryKey: queryKeys.suggestions(),
    queryFn: () => usersApi.getSuggestions(5),
  })

  const followMutation = useMutation({
    mutationFn: (username: string) => followApi.follow(username),
    onSuccess: (_result, username) => {
      queryClient.setQueryData<UserSummary[]>(queryKeys.suggestions(), (current) =>
        current?.filter((s) => s.username !== username),
      )
    },
  })

  if (!user) return null

  return (
    <aside className={styles.sidebar}>
      <div className={styles.ownProfile}>
        <Link to={`/${user.username}`}>
          <Avatar src={user.profilePictureUrl} alt={user.username} size={44} />
        </Link>
        <div className={styles.rowText}>
          <Link to={`/${user.username}`} className={styles.username}>
            {user.username}
          </Link>
          {user.fullName ? <span className={styles.fullName}>{user.fullName}</span> : null}
        </div>
      </div>

      {suggestions && suggestions.length > 0 ? (
        <>
          <div className={styles.header}>Suggested for you</div>
          {suggestions.map((s) => (
            <div key={s.id} className={styles.row}>
              <Link to={`/${s.username}`}>
                <Avatar src={s.profilePictureUrl} alt={s.username} size={32} />
              </Link>
              <div className={styles.rowText}>
                <Link to={`/${s.username}`} className={styles.username}>
                  {s.username}
                  {s.isVerified ? <VerifiedBadge size={11} /> : null}
                </Link>
                {s.fullName ? <span className={styles.fullName}>{s.fullName}</span> : null}
              </div>
              <button
                type="button"
                className={styles.followButton}
                onClick={() => followMutation.mutate(s.username)}
                disabled={followMutation.isPending}
              >
                Follow
              </button>
            </div>
          ))}
        </>
      ) : null}
    </aside>
  )
}
