import { useEffect, useRef, useState } from 'react'
import styles from './MediaEditor.module.css'

const MAX_DIMENSION = 1440

const ASPECT_PRESETS: { label: string; value: number | null }[] = [
  { label: 'Original', value: null },
  { label: '1:1', value: 1 },
  { label: '4:5', value: 4 / 5 },
  { label: '16:9', value: 16 / 9 },
]

const FILTER_PRESETS: { label: string; css: string }[] = [
  { label: 'Normal', css: 'none' },
  { label: 'Clarendon', css: 'contrast(1.2) saturate(1.35)' },
  { label: 'Moon', css: 'grayscale(1) contrast(1.1) brightness(1.1)' },
  { label: 'Lark', css: 'brightness(1.1) contrast(0.9) saturate(1.1)' },
  { label: 'Gingham', css: 'sepia(0.25) brightness(1.05) contrast(0.9)' },
]

interface TextOverlay {
  text: string
  x: number
  y: number
}

/** Client-side-only image editing (crop, filter presets, a draggable text overlay) — entirely
 * canvas-based, no server involvement, so it adds zero infrastructure cost. Only offered for a
 * single image (not carousels, not video — see CreatePostModal/CreateStoryModal for why). The
 * exported file is a flattened PNG the existing presigned-upload path treats like any other file. */
export function MediaEditor({ file, onDone, onCancel }: { file: File; onDone: (file: File) => void; onCancel: () => void }) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const imgRef = useRef<HTMLImageElement | null>(null)
  const draggingRef = useRef(false)

  const [imageLoaded, setImageLoaded] = useState(false)
  const [aspect, setAspect] = useState<number | null>(null)
  const [filter, setFilter] = useState('none')
  const [overlay, setOverlay] = useState<TextOverlay | null>(null)
  const [editingText, setEditingText] = useState(false)
  const [textDraft, setTextDraft] = useState('')

  useEffect(() => {
    const img = new Image()
    const url = URL.createObjectURL(file)
    img.onload = () => {
      imgRef.current = img
      setImageLoaded(true)
    }
    img.src = url
    return () => URL.revokeObjectURL(url)
  }, [file])

  useEffect(() => {
    draw()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [imageLoaded, aspect, filter, overlay])

  function draw() {
    const canvas = canvasRef.current
    const img = imgRef.current
    if (!canvas || !img) return

    const naturalAspect = img.width / img.height
    const targetAspect = aspect ?? naturalAspect
    let width: number
    let height: number
    if (targetAspect >= 1) {
      width = Math.min(MAX_DIMENSION, img.width)
      height = Math.round(width / targetAspect)
    } else {
      height = Math.min(MAX_DIMENSION, img.height)
      width = Math.round(height * targetAspect)
    }
    canvas.width = width
    canvas.height = height

    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.clearRect(0, 0, width, height)
    ctx.filter = filter

    let sx: number
    let sy: number
    let sWidth: number
    let sHeight: number
    if (naturalAspect > targetAspect) {
      sHeight = img.height
      sWidth = sHeight * targetAspect
      sx = (img.width - sWidth) / 2
      sy = 0
    } else {
      sWidth = img.width
      sHeight = sWidth / targetAspect
      sx = 0
      sy = (img.height - sHeight) / 2
    }
    ctx.drawImage(img, sx, sy, sWidth, sHeight, 0, 0, width, height)
    ctx.filter = 'none'

    if (overlay && overlay.text) {
      const fontSize = Math.round(width * 0.07)
      ctx.font = `bold ${fontSize}px sans-serif`
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      ctx.lineWidth = Math.max(2, fontSize * 0.12)
      ctx.strokeStyle = 'rgba(0, 0, 0, 0.6)'
      ctx.fillStyle = '#fff'
      const x = overlay.x * width
      const y = overlay.y * height
      ctx.strokeText(overlay.text, x, y)
      ctx.fillText(overlay.text, x, y)
    }
  }

  function pointerToRelative(e: React.PointerEvent<HTMLCanvasElement>) {
    const canvas = canvasRef.current
    if (!canvas) return null
    const rect = canvas.getBoundingClientRect()
    return {
      x: Math.min(1, Math.max(0, (e.clientX - rect.left) / rect.width)),
      y: Math.min(1, Math.max(0, (e.clientY - rect.top) / rect.height)),
    }
  }

  function handlePointerDown(e: React.PointerEvent<HTMLCanvasElement>) {
    if (!overlay) return
    draggingRef.current = true
    const pos = pointerToRelative(e)
    if (pos) setOverlay({ ...overlay, ...pos })
  }

  function handlePointerMove(e: React.PointerEvent<HTMLCanvasElement>) {
    if (!draggingRef.current || !overlay) return
    const pos = pointerToRelative(e)
    if (pos) setOverlay({ ...overlay, ...pos })
  }

  function handlePointerUp() {
    draggingRef.current = false
  }

  function handleDone() {
    const canvas = canvasRef.current
    if (!canvas) return
    canvas.toBlob((blob) => {
      if (!blob) return
      const editedFile = new File([blob], file.name.replace(/\.\w+$/, '.png'), { type: 'image/png' })
      onDone(editedFile)
    }, 'image/png')
  }

  return (
    <div className={styles.overlay}>
      <div className={styles.panel}>
        <div className={styles.header}>
          <button type="button" className={styles.headerButton} onClick={onCancel}>
            Cancel
          </button>
          <span className={styles.headerTitle}>Edit</span>
          <button type="button" className={[styles.headerButton, styles.headerButtonPrimary].join(' ')} onClick={handleDone}>
            Done
          </button>
        </div>
        <div className={styles.canvasWrapper}>
          <canvas
            ref={canvasRef}
            className={styles.canvas}
            onPointerDown={handlePointerDown}
            onPointerMove={handlePointerMove}
            onPointerUp={handlePointerUp}
            onPointerLeave={handlePointerUp}
          />
        </div>
        <div className={styles.controls}>
          <div className={styles.controlRow}>
            <span className={styles.controlLabel}>Crop</span>
            {ASPECT_PRESETS.map((preset) => (
              <button
                key={preset.label}
                type="button"
                className={[styles.chip, aspect === preset.value ? styles.chipActive : ''].join(' ')}
                onClick={() => setAspect(preset.value)}
              >
                {preset.label}
              </button>
            ))}
          </div>
          <div className={styles.controlRow}>
            <span className={styles.controlLabel}>Filter</span>
            {FILTER_PRESETS.map((preset) => (
              <button
                key={preset.label}
                type="button"
                className={[styles.chip, filter === preset.css ? styles.chipActive : ''].join(' ')}
                onClick={() => setFilter(preset.css)}
              >
                {preset.label}
              </button>
            ))}
          </div>
          <div className={styles.controlRow}>
            <span className={styles.controlLabel}>Text</span>
            {editingText ? (
              <>
                <input
                  className={styles.textInput}
                  value={textDraft}
                  onChange={(e) => setTextDraft(e.target.value)}
                  placeholder="Add text"
                  autoFocus
                  maxLength={60}
                />
                <button
                  type="button"
                  className={styles.chip}
                  onClick={() => {
                    if (textDraft.trim()) setOverlay({ text: textDraft.trim(), x: 0.5, y: 0.5 })
                    setEditingText(false)
                  }}
                >
                  {overlay ? 'Update' : 'Add'}
                </button>
              </>
            ) : (
              <button
                type="button"
                className={styles.chip}
                onClick={() => {
                  setTextDraft(overlay?.text ?? '')
                  setEditingText(true)
                }}
              >
                {overlay ? 'Edit text' : 'Add text'}
              </button>
            )}
            {overlay ? (
              <button type="button" className={styles.chip} onClick={() => setOverlay(null)}>
                Remove
              </button>
            ) : null}
          </div>
          {overlay ? <p className={styles.hint}>Drag the text on the image to reposition it.</p> : null}
        </div>
      </div>
    </div>
  )
}
