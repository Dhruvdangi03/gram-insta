import { useState } from 'react'
import type { Media } from '@/lib/api/types'
import styles from './MediaCarousel.module.css'

interface MediaCarouselProps {
  media: Media[]
  alt: string
  mediaClassName?: string
  autoPlayVideo?: boolean
}

/** Renders one item from a post's media array with prev/next arrows + dot indicators when there's
 * more than one — used by both the feed's PostCard and the PostDetail view so carousel navigation
 * only exists in one place. The parent wrapper must be position:relative (the arrows/dots overlay it). */
export function MediaCarousel({ media, alt, mediaClassName, autoPlayVideo }: MediaCarouselProps) {
  const [index, setIndex] = useState(0)
  const current = media[index]
  if (!current) return null

  const showNav = media.length > 1

  return (
    <>
      {current.mediaType === 'VIDEO' ? (
        <video
          className={mediaClassName}
          src={current.url}
          poster={current.thumbnailUrl ?? undefined}
          controls
          autoPlay={autoPlayVideo}
          muted={autoPlayVideo}
        />
      ) : (
        <img className={mediaClassName} src={current.url} alt={alt} />
      )}
      {showNav ? (
        <>
          {index > 0 ? (
            <button
              type="button"
              className={[styles.arrow, styles.arrowLeft].join(' ')}
              onClick={(e) => {
                e.stopPropagation()
                setIndex((i) => i - 1)
              }}
              aria-label="Previous photo"
            >
              ‹
            </button>
          ) : null}
          {index < media.length - 1 ? (
            <button
              type="button"
              className={[styles.arrow, styles.arrowRight].join(' ')}
              onClick={(e) => {
                e.stopPropagation()
                setIndex((i) => i + 1)
              }}
              aria-label="Next photo"
            >
              ›
            </button>
          ) : null}
          <div className={styles.dots}>
            {media.map((_, i) => (
              <span key={i} className={[styles.dot, i === index ? styles.dotActive : ''].join(' ')} />
            ))}
          </div>
        </>
      ) : null}
    </>
  )
}
