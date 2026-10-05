import { Link, useLocation } from 'react-router-dom'
import { Icon } from '@/components/Icon'
import type { Post } from '@/lib/api/types'
import { formatCount } from '@/lib/formatters/relativeTime'
import styles from './PostGridTile.module.css'

export function PostGridTile({ post }: { post: Post }) {
  const location = useLocation()
  const thumbnail = post.media[0]?.thumbnailUrl ?? post.media[0]?.url

  return (
    <Link to={`/p/${post.id}`} state={{ backgroundLocation: location }} className={styles.tile}>
      {thumbnail ? (
        <img className={styles.media} src={thumbnail} alt={post.caption || `Post by ${post.author.username}`} />
      ) : null}
      {post.type === 'REEL' || post.type === 'VIDEO' ? (
        <Icon name="video" variant="filled" size={18} className={styles.typeBadge} />
      ) : post.type === 'CAROUSEL' ? (
        <Icon name="carousel" size={18} className={styles.typeBadge} />
      ) : null}
      <div className={styles.overlay}>
        <span className={styles.stat}>
          <Icon name="heart" variant="filled" />
          {formatCount(post.likeCount)}
        </span>
        <span className={styles.stat}>
          <Icon name="comment" variant="filled" />
          {formatCount(post.commentCount)}
        </span>
      </div>
    </Link>
  )
}
