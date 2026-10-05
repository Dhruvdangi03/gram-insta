import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { CaptionText } from '@/components/CaptionText'
import { Icon } from '@/components/Icon'
import { MediaCarousel } from '@/components/MediaCarousel'
import { PostOptionsMenu } from '@/components/PostOptionsMenu'
import * as commentsApi from '@/lib/api/endpoints/comments'
import * as postsApi from '@/lib/api/endpoints/posts'
import { formatCount, formatRelativeTime } from '@/lib/formatters/relativeTime'
import { useLikeMutation } from '@/lib/hooks/useLikeMutation'
import { useSaveMutation } from '@/lib/hooks/useSaveMutation'
import { patchPostInAllCaches } from '@/lib/queryHelpers'
import { queryKeys } from '@/lib/queryKeys'
import { CommentList, type ReplyTarget } from './CommentList'
import styles from './PostDetail.module.css'

export function PostDetail({ postId }: { postId: number }) {
  const navigate = useNavigate()
  const { data: post, isLoading, isError } = useQuery({
    queryKey: queryKeys.post(postId),
    queryFn: () => postsApi.getPost(postId),
  })
  const [commentText, setCommentText] = useState('')
  const [replyingTo, setReplyingTo] = useState<ReplyTarget | null>(null)
  const queryClient = useQueryClient()

  const likeMutation = useLikeMutation(postId, post?.likedByViewer ?? false, post?.likeCount ?? 0)
  const saveMutation = useSaveMutation(postId, post?.savedByViewer ?? false)
  const commentMutation = useMutation({
    mutationFn: (input: { text: string; parentCommentId?: number }) =>
      commentsApi.createComment(postId, input),
    onSuccess: () => {
      if (post) patchPostInAllCaches(queryClient, postId, { commentCount: post.commentCount + 1 })
      queryClient.invalidateQueries({ queryKey: queryKeys.comments(postId) })
      setCommentText('')
      setReplyingTo(null)
    },
  })

  if (isError) {
    return (
      <div className={styles.layout}>
        <p className={styles.notFound}>This post isn't available anymore.</p>
      </div>
    )
  }

  if (isLoading || !post) {
    return <div className={styles.layout} />
  }

  function handleSubmitComment() {
    const text = commentText.trim()
    if (!text) return
    commentMutation.mutate({ text, parentCommentId: replyingTo?.id })
  }

  return (
    <div className={styles.layout}>
      <div className={styles.media}>
        <MediaCarousel media={post.media} alt={post.caption || `Post by ${post.author.username}`} autoPlayVideo />
      </div>
      <div className={styles.side}>
        <header className={styles.header}>
          <Link to={`/${post.author.username}`}>
            <Avatar src={post.author.profilePictureUrl} alt={post.author.username} size={32} />
          </Link>
          <Link to={`/${post.author.username}`} className={styles.username}>
            {post.author.username}
          </Link>
          <PostOptionsMenu post={post} onDeleted={() => navigate(-1)} />
        </header>

        {post.caption ? (
          <div className={styles.captionRow}>
            <Avatar src={post.author.profilePictureUrl} alt={post.author.username} size={32} />
            <CaptionText
              username={post.author.username}
              caption={post.caption}
              className={styles.captionText}
              usernameClassName={styles.captionUsername}
              hashtagClassName={styles.hashtag}
            />
          </div>
        ) : null}

        <CommentList
          postId={postId}
          onReply={setReplyingTo}
          onCommentDeleted={(removedCount) =>
            patchPostInAllCaches(queryClient, postId, { commentCount: Math.max(post.commentCount - removedCount, 0) })
          }
        />

        <div className={styles.footer}>
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
            <button type="button" className={styles.actionButton} aria-label="Comment">
              <Icon name="comment" />
            </button>
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
          <p className={styles.likeCount}>
            {formatCount(post.likeCount)} {post.likeCount === 1 ? 'like' : 'likes'}
          </p>
          <time className={styles.timestamp} dateTime={post.createdAt}>
            {formatRelativeTime(post.createdAt)}
          </time>
          {replyingTo ? (
            <div className={styles.replyingToChip}>
              Replying to <strong>@{replyingTo.username}</strong>
              <button type="button" onClick={() => setReplyingTo(null)} aria-label="Cancel reply">
                <Icon name="close" size={12} />
              </button>
            </div>
          ) : null}
          <div className={styles.composer}>
            <input
              className={styles.composerInput}
              placeholder={replyingTo ? `Reply to @${replyingTo.username}…` : 'Add a comment…'}
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
        </div>
      </div>
    </div>
  )
}
