import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { Button } from '@/components/Button'
import { Icon } from '@/components/Icon'
import { MediaEditor } from '@/components/MediaEditor'
import { Modal } from '@/components/Modal'
import { useAuth } from '@/contexts/useAuth'
import { ApiError } from '@/lib/api/client'
import * as postsApi from '@/lib/api/endpoints/posts'
import * as storiesApi from '@/lib/api/endpoints/stories'
import { queryKeys } from '@/lib/queryKeys'
import styles from './CreateStoryModal.module.css'

const ALLOWED_TYPE_PATTERN = /^image\/(jpeg|png|webp)$/

export function CreateStoryModal({ onClose }: { onClose: () => void }) {
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [showEditor, setShowEditor] = useState(false)
  const queryClient = useQueryClient()
  const { user } = useAuth()

  const shareMutation = useMutation({
    mutationFn: async (selected: File) => {
      const upload = await postsApi.createUploadUrl({ contentType: selected.type })
      await postsApi.uploadToPresignedUrl(upload.uploadUrl, selected)
      return storiesApi.createStory({ mediaUrl: upload.publicUrl })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.storiesFeed() })
      if (user) queryClient.invalidateQueries({ queryKey: queryKeys.userStories(user.username) })
      if (previewUrl) URL.revokeObjectURL(previewUrl)
      onClose()
    },
  })

  function selectFile(selected: File | null | undefined) {
    if (!selected) return
    if (!ALLOWED_TYPE_PATTERN.test(selected.type)) {
      setError('Please select a JPEG, PNG, or WebP image.')
      return
    }
    setError(null)
    setFile(selected)
    setPreviewUrl(URL.createObjectURL(selected))
    setShowEditor(true)
  }

  return (
    <>
      <Modal onClose={onClose} contentClassName={styles.content} labelledBy="create-story-title">
        <span id="create-story-title" className={styles.title}>
          Add to your story
        </span>
        <div
          className={styles.dropzone}
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => {
            e.preventDefault()
            selectFile(e.dataTransfer.files[0])
          }}
          onClick={() => !file && fileInputRef.current?.click()}
        >
          {previewUrl ? (
            <img className={styles.preview} src={previewUrl} alt="Story preview" />
          ) : (
            <>
              <Icon name="create" size={48} />
              <span>Drag a photo here or click to select</span>
            </>
          )}
        </div>
        {error ? <p className={styles.errorText}>{error}</p> : null}
        {shareMutation.isError ? (
          <p className={styles.errorText}>
            {shareMutation.error instanceof ApiError ? shareMutation.error.detail : 'Something went wrong.'}
          </p>
        ) : null}
        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          hidden
          onChange={(e) => selectFile(e.target.files?.[0])}
        />
        <Button
          fullWidth
          disabled={!file}
          loading={shareMutation.isPending}
          onClick={() => file && shareMutation.mutate(file)}
        >
          Share to your story
        </Button>
      </Modal>
      {showEditor && file ? (
        <MediaEditor
          file={file}
          onCancel={() => {
            setShowEditor(false)
            if (previewUrl) URL.revokeObjectURL(previewUrl)
            setFile(null)
            setPreviewUrl(null)
          }}
          onDone={(editedFile) => {
            if (previewUrl) URL.revokeObjectURL(previewUrl)
            setFile(editedFile)
            setPreviewUrl(URL.createObjectURL(editedFile))
            setShowEditor(false)
          }}
        />
      ) : null}
    </>
  )
}
