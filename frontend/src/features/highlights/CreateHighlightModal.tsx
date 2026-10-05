import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Button } from '@/components/Button'
import { Modal } from '@/components/Modal'
import * as highlightsApi from '@/lib/api/endpoints/highlights'
import { ApiError } from '@/lib/api/client'
import { queryKeys } from '@/lib/queryKeys'
import styles from './CreateHighlightModal.module.css'

/** Asks for a title, creates the highlight, and adds the given (already-selected) active story
 * to it as its first item — the picker step that chooses `storyId` lives in the caller
 * (NewHighlightFlow for ProfilePage's "+ New", or the "Add to highlight" sheet in StoryViewer). */
export function CreateHighlightModal({ storyId, onClose }: { storyId: number; onClose: () => void }) {
  const [title, setTitle] = useState('')
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: async () => {
      const highlight = await highlightsApi.createHighlight({ title: title.trim() })
      try {
        await highlightsApi.addHighlightItem(highlight.id, storyId)
      } catch (err) {
        // Don't leave a permanent, empty, cover-less highlight behind if the first item fails to add.
        await highlightsApi.deleteHighlight(highlight.id).catch(() => {})
        throw err
      }
      return highlight
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.stories() })
      onClose()
    },
  })

  return (
    <Modal onClose={onClose} contentClassName={styles.content} labelledBy="new-highlight-title">
      <div className={styles.header}>
        <span id="new-highlight-title">New highlight</span>
      </div>
      <div className={styles.body}>
        <input
          className={styles.titleInput}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="Highlight name"
          maxLength={50}
          autoFocus
        />
        <Button onClick={() => mutation.mutate()} disabled={!title.trim() || mutation.isPending}>
          {mutation.isPending ? 'Creating…' : 'Create'}
        </Button>
        {mutation.isError ? (
          <p className={styles.errorText}>
            {mutation.error instanceof ApiError ? mutation.error.detail : 'Something went wrong.'}
          </p>
        ) : null}
      </div>
    </Modal>
  )
}
