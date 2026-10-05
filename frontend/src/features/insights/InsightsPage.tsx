import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Icon } from '@/components/Icon'
import * as usersApi from '@/lib/api/endpoints/users'
import { formatCount } from '@/lib/formatters/relativeTime'
import { queryKeys } from '@/lib/queryKeys'
import styles from './InsightsPage.module.css'

export function InsightsPage() {
  const { data: insights, isLoading } = useQuery({
    queryKey: queryKeys.insights(),
    queryFn: () => usersApi.getInsights(),
  })

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <Link to="/accounts/edit" className={styles.backButton} aria-label="Back">
          <Icon name="back" />
        </Link>
        <h1 className={styles.title}>Insights</h1>
      </header>
      {isLoading || !insights ? null : (
        <div className={styles.grid}>
          <div className={styles.stat}>
            <span className={styles.statValue}>{formatCount(insights.postCount)}</span>
            <span className={styles.statLabel}>Posts</span>
          </div>
          <div className={styles.stat}>
            <span className={styles.statValue}>{formatCount(insights.followerCount)}</span>
            <span className={styles.statLabel}>Followers</span>
          </div>
          <div className={styles.stat}>
            <span className={styles.statValue}>{formatCount(insights.followingCount)}</span>
            <span className={styles.statLabel}>Following</span>
          </div>
          <div className={styles.stat}>
            <span className={styles.statValue}>{formatCount(insights.totalLikes)}</span>
            <span className={styles.statLabel}>Total likes</span>
          </div>
          <div className={styles.stat}>
            <span className={styles.statValue}>{formatCount(insights.totalComments)}</span>
            <span className={styles.statLabel}>Total comments</span>
          </div>
        </div>
      )}
    </div>
  )
}
