import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { CaptionText } from '@/components/CaptionText'
import { Icon } from '@/components/Icon'
import { PostOptionsMenu } from '@/components/PostOptionsMenu'
import type { Post } from '@/lib/api/types'
import { formatCount } from '@/lib/formatters/relativeTime'
import { useLikeMutation } from '@/lib/hooks/useLikeMutation'
import styles from './ReelItem.module.css'

export function ReelItem({ post }: { post: Post }) {
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const containerRef = useRef<HTMLDivElement | null>(null)
  const [muted, setMuted] = useState(true)
  const likeMutation = useLikeMutation(post.id, post.likedByViewer, post.likeCount)
  const media = post.media[0]

  useEffect(() => {
    const video = videoRef.current
    const container = containerRef.current
    if (!video || !container) return

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          video.play().catch(() => {
            // Autoplay can be blocked before any user gesture on the page — the mute toggle still works.
          })
        } else {
          video.pause()
        }
      },
      { threshold: 0.6 },
    )
    observer.observe(container)
    return () => observer.disconnect()
  }, [])

  return (
    <div ref={containerRef} className={styles.item}>
      <div className={styles.videoWrapper}>
        {media ? (
          <video
            ref={videoRef}
            className={styles.video}
            src={media.url}
            poster={media.thumbnailUrl ?? undefined}
            loop
            muted={muted}
            playsInline
            onClick={() => (videoRef.current?.paused ? videoRef.current?.play() : videoRef.current?.pause())}
          />
        ) : null}
        <button type="button" className={styles.muteButton} onClick={() => setMuted((m) => !m)} aria-label={muted ? 'Unmute' : 'Mute'}>
          <Icon name={muted ? 'volumeOff' : 'volumeOn'} size={18} />
        </button>

        <div className={styles.overlayBottom}>
          <div className={styles.authorRow}>
            <Avatar src={post.author.profilePictureUrl} alt={post.author.username} size={32} />
            <Link to={`/${post.author.username}`} className={styles.username}>
              {post.author.username}
            </Link>
          </div>
          {post.caption ? (
            <CaptionText
              username={post.author.username}
              showUsername={false}
              caption={post.caption}
              className={styles.caption}
              hashtagClassName={styles.hashtag}
            />
          ) : null}
        </div>

        <div className={styles.sideActions}>
          <button
            type="button"
            className={[styles.actionButton, post.likedByViewer ? styles.liked : ''].join(' ')}
            onClick={() => likeMutation.mutate()}
            aria-pressed={post.likedByViewer}
            aria-label={post.likedByViewer ? 'Unlike' : 'Like'}
          >
            <Icon name="heart" variant={post.likedByViewer ? 'filled' : 'outline'} size={28} />
            <span className={styles.count}>{formatCount(post.likeCount)}</span>
          </button>
          <Link to={`/p/${post.id}`} className={styles.actionButton}>
            <Icon name="comment" size={28} />
            <span className={styles.count}>{formatCount(post.commentCount)}</span>
          </Link>
          <button type="button" className={styles.actionButton} aria-label="Share">
            <Icon name="share" size={28} />
          </button>
          <PostOptionsMenu post={post} className={styles.actionButton} />
        </div>
      </div>
    </div>
  )
}
