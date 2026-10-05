import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ActionSheet } from '@/components/ActionSheet'
import { EditCaptionModal } from '@/components/EditCaptionModal'
import { Icon } from '@/components/Icon'
import { useAuth } from '@/contexts/useAuth'
import * as postsApi from '@/lib/api/endpoints/posts'
import * as reportsApi from '@/lib/api/endpoints/reports'
import * as usersApi from '@/lib/api/endpoints/users'
import type { Post } from '@/lib/api/types'
import { queryKeys } from '@/lib/queryKeys'
import { removePostFromAllCaches } from '@/lib/queryHelpers'

/** The post "..." menu — own post gets Edit/Delete; someone else's post gets Report/Block (Block
 * is a one-way action here, not a toggle — Unblock lives on the profile options menu where the
 * current block state is actually known). Shared by PostCard (feed/grid) and PostDetail
 * (modal/page) so the menu only exists in one place. */
export function PostOptionsMenu({ post, className, onDeleted }: { post: Post; className?: string; onDeleted?: () => void }) {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [showSheet, setShowSheet] = useState(false)
  const [showEdit, setShowEdit] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [showBlockConfirm, setShowBlockConfirm] = useState(false)
  const [showReported, setShowReported] = useState(false)

  const isOwn = user?.username === post.author.username

  const deleteMutation = useMutation({
    mutationFn: () => postsApi.deletePost(post.id),
    onSuccess: () => {
      removePostFromAllCaches(queryClient, post.id)
      queryClient.invalidateQueries({ queryKey: queryKeys.userProfile(post.author.username) })
      onDeleted?.()
    },
    onError: () => window.alert('Something went wrong deleting this post. Please try again.'),
  })

  const blockMutation = useMutation({
    mutationFn: () => usersApi.blockUser(post.author.username),
    onSuccess: () => {
      // Evicting just this one post isn't enough — the blocked author's other posts may already
      // be cached in the feed/explore lists, and they should also drop out of Suggested Accounts.
      removePostFromAllCaches(queryClient, post.id)
      queryClient.invalidateQueries({ queryKey: queryKeys.userProfile(post.author.username) })
      queryClient.invalidateQueries({ queryKey: queryKeys.feed() })
      queryClient.invalidateQueries({ queryKey: queryKeys.explore() })
      queryClient.invalidateQueries({ queryKey: queryKeys.suggestions() })
      onDeleted?.()
    },
    onError: () => window.alert(`Something went wrong blocking @${post.author.username}. Please try again.`),
  })

  const reportMutation = useMutation({
    mutationFn: () => reportsApi.report({ targetType: 'POST', targetId: post.id }),
    // Removing the post from caches happens once the confirmation is dismissed (see the
    // showReported sheet's onClose below), not here — doing it here would unmount this very
    // component (and the sheet it's about to show) before the user ever sees it, on any surface
    // where this post is cached in a list (e.g. the home feed).
    onSuccess: () => setShowReported(true),
    onError: () => window.alert('Something went wrong reporting this post. Please try again.'),
  })

  return (
    <>
      <button type="button" className={className} aria-label="More options" onClick={() => setShowSheet(true)}>
        <Icon name="options" />
      </button>
      {showSheet ? (
        <ActionSheet
          onClose={() => setShowSheet(false)}
          actions={
            isOwn
              ? [
                  { label: 'Edit', onClick: () => setShowEdit(true) },
                  { label: 'Delete', onClick: () => setShowDeleteConfirm(true), destructive: true },
                ]
              : [
                  { label: 'Report', onClick: () => reportMutation.mutate(), destructive: true },
                  { label: `Block @${post.author.username}`, onClick: () => setShowBlockConfirm(true), destructive: true },
                ]
          }
        />
      ) : null}
      {showEdit ? <EditCaptionModal post={post} onClose={() => setShowEdit(false)} /> : null}
      {showDeleteConfirm ? (
        <ActionSheet
          onClose={() => setShowDeleteConfirm(false)}
          actions={[
            {
              label: deleteMutation.isPending ? 'Deleting…' : 'Delete',
              onClick: () => deleteMutation.mutate(),
              destructive: true,
            },
          ]}
        />
      ) : null}
      {showBlockConfirm ? (
        <ActionSheet
          onClose={() => setShowBlockConfirm(false)}
          actions={[
            {
              label: blockMutation.isPending ? 'Blocking…' : `Block @${post.author.username}`,
              onClick: () => blockMutation.mutate(),
              destructive: true,
            },
          ]}
        />
      ) : null}
      {showReported ? (
        <ActionSheet
          onClose={() => {
            setShowReported(false)
            removePostFromAllCaches(queryClient, post.id)
          }}
          actions={[{ label: "Thanks for reporting this. We won't show it to you again.", onClick: () => {} }]}
        />
      ) : null}
    </>
  )
}
