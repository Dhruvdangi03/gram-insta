import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Modal } from '@/components/Modal'
import * as postsApi from '@/lib/api/endpoints/posts'
import { ApiError } from '@/lib/api/client'
import type { Post } from '@/lib/api/types'
import { patchPostInAllCaches } from '@/lib/queryHelpers'
import styles from './EditCaptionModal.module.css'

export function EditCaptionModal({ post, onClose }: { post: Post; onClose: () => void }) {
  const [caption, setCaption] = useState(post.caption ?? '')
  const [location, setLocation] = useState(post.location ?? '')
  const queryClient = useQueryClient()

  const mutation = useMutation({
    // Send the fields as explicit strings (including ""), not `|| undefined` — the backend now
    // treats an omitted field as "leave unchanged" and only an explicit "" as "clear this field".
    mutationFn: () => postsApi.updatePost(post.id, { caption, location }),
    onSuccess: (updated) => {
      patchPostInAllCaches(queryClient, post.id, {
        caption: updated.caption,
        location: updated.location,
      })
      onClose()
    },
  })

  return (
    <Modal onClose={onClose} contentClassName={styles.content} labelledBy="edit-caption-title">
      <div className={styles.header}>
        <span id="edit-caption-title">Edit info</span>
        <button type="button" className={styles.doneButton} onClick={() => mutation.mutate()} disabled={mutation.isPending}>
          {mutation.isPending ? 'Saving…' : 'Done'}
        </button>
      </div>
      <div className={styles.body}>
        <textarea
          className={styles.captionInput}
          value={caption}
          maxLength={2200}
          placeholder="Write a caption…"
          onChange={(e) => setCaption(e.target.value)}
        />
        <input
          className={styles.locationInput}
          value={location}
          maxLength={255}
          placeholder="Add location"
          onChange={(e) => setLocation(e.target.value)}
        />
        {mutation.isError ? (
          <p className={styles.errorText}>
            {mutation.error instanceof ApiError ? mutation.error.detail : 'Something went wrong.'}
          </p>
        ) : null}
      </div>
    </Modal>
  )
}
