import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Icon } from '@/components/Icon'
import { MediaEditor } from '@/components/MediaEditor'
import { Modal } from '@/components/Modal'
import * as postsApi from '@/lib/api/endpoints/posts'
import * as reelsApi from '@/lib/api/endpoints/reels'
import { ApiError } from '@/lib/api/client'
import { useAuth } from '@/contexts/useAuth'
import { queryKeys } from '@/lib/queryKeys'
import styles from './CreatePostModal.module.css'

const ALLOWED_IMAGE_PATTERN = /^image\/(jpeg|png|webp)$/
const ALLOWED_TYPE_PATTERN = /^image\/(jpeg|png|webp)$|^video\/(mp4|quicktime|webm)$/
const CAPTION_MAX = 2200
const LOCATION_MAX = 255
const MAX_CAROUSEL_ITEMS = 10

interface CreatePostInput {
  files: File[]
  caption: string
  location: string
}

export function CreatePostModal() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const addMoreInputRef = useRef<HTMLInputElement | null>(null)

  const [files, setFiles] = useState<File[]>([])
  const [previews, setPreviews] = useState<string[]>([])
  const [activeIndex, setActiveIndex] = useState(0)
  const [caption, setCaption] = useState('')
  const [location, setLocation] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)
  const [showEditor, setShowEditor] = useState(false)

  const createMutation = useMutation({
    mutationFn: async ({ files, caption, location }: CreatePostInput) => {
      const isVideo = files.length === 1 && files[0].type.startsWith('video/')
      if (isVideo) {
        const upload = await postsApi.createUploadUrl({ contentType: files[0].type })
        await postsApi.uploadToPresignedUrl(upload.uploadUrl, files[0])
        return reelsApi.createReel({
          caption: caption || undefined,
          location: location || undefined,
          media: { url: upload.publicUrl },
        })
      }
      const media = await Promise.all(
        files.map(async (file) => {
          const upload = await postsApi.createUploadUrl({ contentType: file.type })
          await postsApi.uploadToPresignedUrl(upload.uploadUrl, file)
          return { url: upload.publicUrl }
        }),
      )
      return postsApi.createPost({ caption: caption || undefined, location: location || undefined, media })
    },
    onSuccess: (post) => {
      if (user) {
        queryClient.invalidateQueries({ queryKey: queryKeys.userPosts(user.username) })
        queryClient.invalidateQueries({ queryKey: queryKeys.userProfile(user.username) })
      }
      navigate(post.type === 'REEL' ? '/reels' : `/${user?.username}`)
    },
  })

  function handleClose() {
    previews.forEach((url) => URL.revokeObjectURL(url))
    navigate(-1)
  }

  function selectFiles(selected: FileList | null | undefined) {
    if (!selected || selected.length === 0) return
    const list = Array.from(selected)

    if (list.length === 1 && ALLOWED_TYPE_PATTERN.test(list[0].type)) {
      setValidationError(null)
      setFiles([list[0]])
      setPreviews([URL.createObjectURL(list[0])])
      setActiveIndex(0)
      // Editing tools only make sense for a still image — a video goes straight to the details step.
      if (!list[0].type.startsWith('video/')) {
        setShowEditor(true)
      }
      return
    }

    if (!list.every((f) => ALLOWED_IMAGE_PATTERN.test(f.type))) {
      setValidationError(
        list.length > 1
          ? 'Multi-select only supports JPEG, PNG, or WebP images.'
          : 'Please select a JPEG, PNG, WebP image or MP4, MOV, WebM video.',
      )
      return
    }
    if (list.length > MAX_CAROUSEL_ITEMS) {
      setValidationError(`You can select up to ${MAX_CAROUSEL_ITEMS} photos.`)
      return
    }
    setValidationError(null)
    setFiles(list)
    setPreviews(list.map((f) => URL.createObjectURL(f)))
    setActiveIndex(0)
  }

  function addMoreFiles(selected: FileList | null | undefined) {
    if (!selected || selected.length === 0) return
    const additions = Array.from(selected)
    if (!additions.every((f) => ALLOWED_IMAGE_PATTERN.test(f.type))) {
      setValidationError('You can only add JPEG, PNG, or WebP images to a carousel.')
      return
    }
    if (files.length + additions.length > MAX_CAROUSEL_ITEMS) {
      setValidationError(`You can select up to ${MAX_CAROUSEL_ITEMS} photos.`)
      return
    }
    setValidationError(null)
    setFiles((prev) => [...prev, ...additions])
    setPreviews((prev) => [...prev, ...additions.map((f) => URL.createObjectURL(f))])
  }

  function removeAt(index: number) {
    URL.revokeObjectURL(previews[index])
    setFiles((prev) => prev.filter((_, i) => i !== index))
    setPreviews((prev) => prev.filter((_, i) => i !== index))
    setActiveIndex((prev) => Math.max(0, Math.min(prev, files.length - 2)))
  }

  function moveTo(index: number, direction: -1 | 1) {
    const target = index + direction
    if (target < 0 || target >= files.length) return
    setFiles((prev) => swap(prev, index, target))
    setPreviews((prev) => swap(prev, index, target))
    setActiveIndex(target)
  }

  function handleShare() {
    if (files.length === 0) return
    createMutation.mutate({ files, caption, location })
  }

  const isVideo = files.length === 1 && files[0]?.type.startsWith('video/')
  const isCarousel = files.length > 1

  return (
    <>
    <Modal onClose={handleClose} contentClassName={styles.content} labelledBy="create-post-title">
      <div className={styles.header}>
        <span id="create-post-title">Create new post</span>
        {files.length > 0 ? (
          <button type="button" className={styles.shareButton} onClick={handleShare} disabled={createMutation.isPending}>
            {createMutation.isPending ? 'Sharing…' : 'Share'}
          </button>
        ) : null}
      </div>

      {files.length === 0 ? (
        <div
          className={styles.selectStep}
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => {
            e.preventDefault()
            selectFiles(e.dataTransfer.files)
          }}
        >
          <Icon name="video" size={64} className={styles.dropzoneIcon} />
          <p className={styles.dropzoneText}>Drag photos and videos here</p>
          {validationError ? <p className={styles.errorText}>{validationError}</p> : null}
          <button type="button" onClick={() => fileInputRef.current?.click()}>
            <span style={{ color: 'var(--color-accent)', fontWeight: 'var(--font-weight-semibold)' }}>
              Select from computer
            </span>
          </button>
          <input
            ref={fileInputRef}
            type="file"
            multiple
            accept="image/jpeg,image/png,image/webp,video/mp4,video/quicktime,video/webm"
            hidden
            onChange={(e) => selectFiles(e.target.files)}
          />
        </div>
      ) : (
        <div className={styles.detailsStep}>
          <div className={styles.previewColumn}>
            {isVideo ? (
              <video src={previews[0]} controls />
            ) : (
              <img src={previews[activeIndex]} alt={`Selected upload preview ${activeIndex + 1}`} />
            )}
            {isCarousel ? (
              <div className={styles.carouselDots}>
                {previews.map((_, i) => (
                  <span key={i} className={[styles.dot, i === activeIndex ? styles.dotActive : ''].join(' ')} />
                ))}
              </div>
            ) : null}
          </div>
          <div className={styles.detailsColumn}>
            <div className={styles.authorRow}>
              <Avatar src={user?.profilePictureUrl} alt={user?.username ?? ''} size={32} />
              <span className={styles.username}>{user?.username}</span>
            </div>

            {!isVideo ? (
              <div className={styles.thumbnailStrip}>
                {previews.map((src, i) => (
                  <div
                    key={i}
                    className={[styles.thumbnail, i === activeIndex ? styles.thumbnailActive : ''].join(' ')}
                    onClick={() => setActiveIndex(i)}
                  >
                    <img src={src} alt={`Thumbnail ${i + 1}`} />
                    <button
                      type="button"
                      className={styles.thumbnailRemove}
                      onClick={(e) => {
                        e.stopPropagation()
                        removeAt(i)
                      }}
                      aria-label={`Remove photo ${i + 1}`}
                    >
                      <Icon name="close" size={12} />
                    </button>
                    {files.length > 1 ? (
                      <div className={styles.thumbnailReorder}>
                        <button
                          type="button"
                          disabled={i === 0}
                          onClick={(e) => {
                            e.stopPropagation()
                            moveTo(i, -1)
                          }}
                          aria-label={`Move photo ${i + 1} earlier`}
                        >
                          ‹
                        </button>
                        <button
                          type="button"
                          disabled={i === files.length - 1}
                          onClick={(e) => {
                            e.stopPropagation()
                            moveTo(i, 1)
                          }}
                          aria-label={`Move photo ${i + 1} later`}
                        >
                          ›
                        </button>
                      </div>
                    ) : null}
                  </div>
                ))}
                {files.length < MAX_CAROUSEL_ITEMS ? (
                  <button
                    type="button"
                    className={styles.addMoreButton}
                    onClick={() => addMoreInputRef.current?.click()}
                    aria-label="Add more photos"
                  >
                    <Icon name="create" size={20} />
                  </button>
                ) : null}
                <input
                  ref={addMoreInputRef}
                  type="file"
                  multiple
                  accept="image/jpeg,image/png,image/webp"
                  hidden
                  onChange={(e) => {
                    addMoreFiles(e.target.files)
                    e.target.value = ''
                  }}
                />
              </div>
            ) : null}

            <textarea
              className={styles.captionInput}
              placeholder="Write a caption…"
              value={caption}
              maxLength={CAPTION_MAX}
              onChange={(e) => setCaption(e.target.value)}
            />
            <span className={styles.charCount}>
              {caption.length}/{CAPTION_MAX}
            </span>
            <input
              className={styles.locationInput}
              placeholder="Add location"
              value={location}
              maxLength={LOCATION_MAX}
              onChange={(e) => setLocation(e.target.value)}
            />
            {validationError ? <p className={styles.errorText}>{validationError}</p> : null}
            {createMutation.isPending ? <p className={styles.progressText}>Uploading…</p> : null}
            {createMutation.isError ? (
              <p className={styles.errorText}>
                {createMutation.error instanceof ApiError ? createMutation.error.detail : 'Something went wrong.'}
              </p>
            ) : null}
          </div>
        </div>
      )}
    </Modal>
    {showEditor && files.length === 1 ? (
      <MediaEditor
        file={files[0]}
        onCancel={() => {
          setShowEditor(false)
          previews.forEach((url) => URL.revokeObjectURL(url))
          setFiles([])
          setPreviews([])
        }}
        onDone={(editedFile) => {
          URL.revokeObjectURL(previews[0])
          setFiles([editedFile])
          setPreviews([URL.createObjectURL(editedFile)])
          setShowEditor(false)
        }}
      />
    ) : null}
    </>
  )
}

function swap<T>(arr: T[], i: number, j: number): T[] {
  const copy = [...arr]
  ;[copy[i], copy[j]] = [copy[j], copy[i]]
  return copy
}
