import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ActionSheet } from '@/components/ActionSheet'
import { EditCaptionModal } from '@/components/EditCaptionModal'
import { Icon } from '@/components/Icon'
import { ReportSheet } from '@/components/ReportSheet'
import { useAuth } from '@/contexts/useAuth'
import * as postsApi from '@/lib/api/endpoints/posts'
import type { Post } from '@/lib/api/types'
import { queryKeys } from '@/lib/queryKeys'
import { removePostFromAllCaches } from '@/lib/queryHelpers'

/** The post "..." menu — Edit/Delete on your own post, Report on someone else's. Shared by PostCard (feed/grid) and PostDetail (modal/page)
 * so the menu only exists in one place. */
export function PostOptionsMenu({ post, className, onDeleted }: { post: Post; className?: string; onDeleted?: () => void }) {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [showSheet, setShowSheet] = useState(false)
  const [showEdit, setShowEdit] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [showReport, setShowReport] = useState(false)

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

  if (!user) {
    return null
  }

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
              : [{ label: 'Report', onClick: () => setShowReport(true), destructive: true }]
          }
        />
      ) : null}
      {showReport ? <ReportSheet target={{ type: 'POST', id: post.id }} onClose={() => setShowReport(false)} /> : null}
      {showEdit ? <EditCaptionModal post={post} onClose={() => setShowEdit(false)} /> : null}
      {showDeleteConfirm ? (
        <ActionSheet
          onClose={() => setShowDeleteConfirm(false)}
          title="Delete post?"
          message="Are you sure you want to delete this post?"
          actions={[
            {
              label: deleteMutation.isPending ? 'Deleting…' : 'Delete',
              onClick: () => deleteMutation.mutate(),
              destructive: true,
            },
          ]}
        />
      ) : null}
    </>
  )
}
