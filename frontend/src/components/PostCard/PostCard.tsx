import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { CaptionText } from '@/components/CaptionText'
import { Icon } from '@/components/Icon'
import { MediaCarousel } from '@/components/MediaCarousel'
import { PostOptionsMenu } from '@/components/PostOptionsMenu'
import { VerifiedBadge } from '@/components/VerifiedBadge'
import * as commentsApi from '@/lib/api/endpoints/comments'
import type { Post } from '@/lib/api/types'
import { formatCount, formatRelativeTime } from '@/lib/formatters/relativeTime'
import { useLikeMutation } from '@/lib/hooks/useLikeMutation'
import { useSaveMutation } from '@/lib/hooks/useSaveMutation'
import { patchPostInAllCaches } from '@/lib/queryHelpers'
import styles from './PostCard.module.css'

export interface PostCardProps {
  post: Post
  /** Detail-view layout skips the "view all comments" link and inline composer summary — the modal/page renders the full thread instead. */
  variant?: 'feed' | 'detail'
}

export function PostCard({ post, variant = 'feed' }: PostCardProps) {
  const location = useLocation()
  // Opening the detail view from the feed overlays a modal and keeps the feed mounted behind it
  // (react-router's "background location" recipe); a direct load of /p/:id later renders the full
  // standalone page instead, since there's no backgroundLocation in that navigation's state.
  const detailLinkState = { backgroundLocation: location }
  const [showHeartBurst, setShowHeartBurst] = useState(false)
  const [commentText, setCommentText] = useState('')
  const queryClient = useQueryClient()

  const likeMutation = useLikeMutation(post.id, post.likedByViewer, post.likeCount)
  const saveMutation = useSaveMutation(post.id, post.savedByViewer)

  const commentMutation = useMutation({
    mutationFn: (text: string) => commentsApi.createComment(post.id, { text }),
    onSuccess: () => {
      patchPostInAllCaches(queryClient, post.id, { commentCount: post.commentCount + 1 })
      queryClient.invalidateQueries({ queryKey: ['comments', post.id] })
      setCommentText('')
    },
  })

  function handleDoubleTapLike() {
    setShowHeartBurst(true)
    window.setTimeout(() => setShowHeartBurst(false), 700)
    if (!post.likedByViewer) {
      likeMutation.mutate()
    }
  }

  function handleSubmitComment() {
    const text = commentText.trim()
    if (!text) return
    commentMutation.mutate(text)
  }

  return (
    <article className={styles.card}>
      <header className={styles.header}>
        <Link to={`/${post.author.username}`}>
          <Avatar src={post.author.profilePictureUrl} alt={post.author.username} size={32} />
        </Link>
        <div className={styles.headerText}>
          <Link to={`/${post.author.username}`} className={styles.username}>
            {post.author.username}
            {post.author.isVerified ? <VerifiedBadge size={12} className={styles.verifiedBadge} /> : null}
          </Link>
          {post.location ? <span className={styles.location}>{post.location}</span> : null}
        </div>
        <PostOptionsMenu post={post} className={styles.optionsButton} />
      </header>

      <div className={styles.mediaWrapper} onDoubleClick={handleDoubleTapLike}>
        <MediaCarousel media={post.media} alt={post.caption || `Post by ${post.author.username}`} mediaClassName={styles.media} />
        <Icon
          name="heart"
          variant="filled"
          size={80}
          className={[styles.doubleTapHeart, showHeartBurst ? styles.doubleTapHeartShown : ''].join(' ')}
        />
      </div>

      <div className={styles.actions}>
        <button
          type="button"
          className={[styles.actionButton, post.likedByViewer ? styles.liked : ''].join(' ')}
          onClick={() => likeMutation.mutate()}
          aria-pressed={post.likedByViewer}
          aria-label={post.likedByViewer ? 'Unlike' : 'Like'}
        >
          <Icon name="heart" variant={post.likedByViewer ? 'filled' : 'outline'} />
        </button>
        <Link to={`/p/${post.id}`} state={detailLinkState} className={styles.actionButton} aria-label="Comment">
          <Icon name="comment" />
        </Link>
        <button type="button" className={styles.actionButton} aria-label="Share">
          <Icon name="share" />
        </button>
        <button
          type="button"
          className={[styles.actionButton, styles.bookmark].join(' ')}
          onClick={() => saveMutation.mutate()}
          aria-pressed={post.savedByViewer}
          aria-label={post.savedByViewer ? 'Remove from saved' : 'Save'}
        >
          <Icon name="bookmark" variant={post.savedByViewer ? 'filled' : 'outline'} />
        </button>
      </div>

      <div className={styles.body}>
        <p className={styles.likeCount}>
          {formatCount(post.likeCount)} {post.likeCount === 1 ? 'like' : 'likes'}
        </p>
        {post.caption ? (
          <CaptionText
            username={post.author.username}
            caption={post.caption}
            className={styles.caption}
            usernameClassName={styles.captionUsername}
            hashtagClassName={styles.hashtag}
          />
        ) : null}
        {variant === 'feed' && post.commentCount > 0 ? (
          <Link to={`/p/${post.id}`} state={detailLinkState} className={styles.viewComments}>
            View all {formatCount(post.commentCount)} comments
          </Link>
        ) : null}
        <time className={styles.timestamp} dateTime={post.createdAt}>
          {formatRelativeTime(post.createdAt)}
        </time>
      </div>

      {variant === 'feed' ? (
        <div className={styles.composer}>
          <input
            className={styles.composerInput}
            placeholder="Add a comment…"
            value={commentText}
            onChange={(e) => setCommentText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleSubmitComment()
            }}
            maxLength={2200}
          />
          <button
            type="button"
            className={styles.postButton}
            disabled={!commentText.trim() || commentMutation.isPending}
            onClick={handleSubmitComment}
          >
            Post
          </button>
        </div>
      ) : null}
    </article>
  )
}
