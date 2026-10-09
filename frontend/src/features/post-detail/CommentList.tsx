import { useMutation, useQueryClient, type InfiniteData } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Icon } from '@/components/Icon'
import { ReportSheet } from '@/components/ReportSheet'
import { RichText } from '@/components/RichText'
import { VerifiedBadge } from '@/components/VerifiedBadge'
import { useAuth } from '@/contexts/useAuth'
import * as commentsApi from '@/lib/api/endpoints/comments'
import * as likesApi from '@/lib/api/endpoints/likes'
import type { Comment, CursorPage } from '@/lib/api/types'
import { formatRelativeTime } from '@/lib/formatters/relativeTime'
import { useCursorInfiniteQuery } from '@/lib/hooks/useCursorInfiniteQuery'
import { useInfiniteScrollSentinel } from '@/lib/hooks/useInfiniteScrollSentinel'
import { queryKeys } from '@/lib/queryKeys'
import styles from './CommentList.module.css'

export interface ReplyTarget {
  id: number
  username: string
}

export function CommentList({
  postId,
  onReply,
  onCommentDeleted,
}: {
  postId: number
  onReply: (target: ReplyTarget) => void
  onCommentDeleted?: (removedCount: number) => void
}) {
  const { items, isLoading, hasNextPage, fetchNextPage, isFetchingNextPage } = useCursorInfiniteQuery(
    queryKeys.comments(postId),
    (cursor) => commentsApi.getComments(postId, cursor),
  )
  const sentinelRef = useInfiniteScrollSentinel(() => fetchNextPage(), Boolean(hasNextPage) && !isFetchingNextPage)

  if (isLoading) {
    return <div className={styles.list} />
  }

  if (items.length === 0) {
    return (
      <div className={styles.list}>
        <p className={styles.empty}>No comments yet.</p>
      </div>
    )
  }

  const repliesByParent = new Map<number, Comment[]>()
  const topLevel: Comment[] = []
  for (const comment of items) {
    if (comment.parentCommentId == null) {
      topLevel.push(comment)
    } else {
      const list = repliesByParent.get(comment.parentCommentId) ?? []
      list.push(comment)
      repliesByParent.set(comment.parentCommentId, list)
    }
  }

  return (
    <div className={styles.list}>
      {topLevel.map((comment) => (
        <CommentRow
          key={comment.id}
          postId={postId}
          comment={comment}
          onReply={onReply}
          onCommentDeleted={onCommentDeleted}
          replies={repliesByParent.get(comment.id)}
        />
      ))}
      <div ref={sentinelRef} className={styles.sentinel} />
    </div>
  )
}

function CommentRow({
  postId,
  comment,
  replies,
  onReply,
  onCommentDeleted,
  isReply = false,
}: {
  postId: number
  comment: Comment
  replies?: Comment[]
  onReply: (target: ReplyTarget) => void
  onCommentDeleted?: (removedCount: number) => void
  isReply?: boolean
}) {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [showReport, setShowReport] = useState(false)
  const deleteMutation = useMutation({
    mutationFn: () => commentsApi.deleteComment(comment.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.comments(postId) })
      onCommentDeleted?.(1 + (replies?.length ?? 0))
    },
    onError: () => window.alert('Something went wrong deleting this comment. Please try again.'),
  })

  // Comments (unlike posts) are only ever cached under one key — this postId's comment list — so
  // a direct patch of that one cache entry is enough; no cross-cache helper like
  // patchPostInAllCaches is needed here.
  const patchComment = (commentId: number, next: { likeCount: number; likedByViewer: boolean }) => {
    queryClient.setQueryData<InfiniteData<CursorPage<Comment>>>(queryKeys.comments(postId), (data) => {
      if (!data) return data
      return {
        ...data,
        pages: data.pages.map((page) => ({
          ...page,
          items: page.items.map((c) => (c.id === commentId ? { ...c, ...next } : c)),
        })),
      }
    })
  }

  const likeMutation = useMutation({
    mutationFn: () =>
      comment.likedByViewer ? likesApi.unlikeComment(comment.id) : likesApi.likeComment(comment.id),
    onMutate: () => {
      const previous = { likeCount: comment.likeCount, likedByViewer: comment.likedByViewer }
      patchComment(comment.id, {
        likeCount: comment.likedByViewer ? Math.max(0, comment.likeCount - 1) : comment.likeCount + 1,
        likedByViewer: !comment.likedByViewer,
      })
      return { previous }
    },
    onError: (_err, _vars, context) => {
      if (context) patchComment(comment.id, context.previous)
    },
    onSuccess: (response) => patchComment(comment.id, response),
  })

  return (
    <div className={isReply ? styles.replyRow : styles.row}>
      <Link to={`/${comment.author.username}`}>
        <Avatar src={comment.author.profilePictureUrl} alt={comment.author.username} size={24} />
      </Link>
      <div className={styles.content}>
        <p className={styles.text}>
          <Link to={`/${comment.author.username}`} className={styles.username}>
            {comment.author.username}
            {comment.author.isVerified ? <VerifiedBadge size={11} className={styles.verifiedBadge} /> : null}
          </Link>
          <RichText text={comment.text} linkClassName={styles.entityLink} />
        </p>
        <div className={styles.meta}>
          <span className={styles.timestamp}>{formatRelativeTime(comment.createdAt)}</span>
          {comment.likeCount > 0 ? (
            <span className={styles.likeCount}>
              {comment.likeCount} {comment.likeCount === 1 ? 'like' : 'likes'}
            </span>
          ) : null}
          <button
            type="button"
            className={styles.replyButton}
            onClick={() => onReply({ id: comment.id, username: comment.author.username })}
          >
            Reply
          </button>
          {user?.username === comment.author.username ? (
            <button
              type="button"
              className={styles.replyButton}
              onClick={() => deleteMutation.mutate()}
              disabled={deleteMutation.isPending}
            >
              Delete
            </button>
          ) : (
            <button type="button" className={styles.replyButton} onClick={() => setShowReport(true)}>
              Report
            </button>
          )}
        </div>
        {showReport ? (
          <ReportSheet target={{ type: 'COMMENT', id: comment.id }} onClose={() => setShowReport(false)} />
        ) : null}
        {replies && replies.length > 0 ? (
          <div className={styles.replies}>
            {replies.map((reply) => (
              <CommentRow
                key={reply.id}
                postId={postId}
                comment={reply}
                onReply={onReply}
                onCommentDeleted={onCommentDeleted}
                isReply
              />
            ))}
          </div>
        ) : null}
      </div>
      <button
        type="button"
        className={[styles.likeButton, comment.likedByViewer ? styles.liked : ''].join(' ')}
        onClick={() => likeMutation.mutate()}
        disabled={likeMutation.isPending}
        aria-pressed={comment.likedByViewer}
        aria-label={comment.likedByViewer ? 'Unlike comment' : 'Like comment'}
      >
        <Icon name="heart" size={12} variant={comment.likedByViewer ? 'filled' : 'outline'} />
      </button>
    </div>
  )
}
