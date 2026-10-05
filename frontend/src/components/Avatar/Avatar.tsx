import { Icon } from '@/components/Icon'
import styles from './Avatar.module.css'

export type AvatarSize = 24 | 32 | 44 | 56 | 77 | 150

export interface AvatarProps {
  src?: string | null
  alt: string
  size?: AvatarSize
  /** Wraps the avatar in Instagram's gradient story ring. 'seen' renders a gray ring instead. */
  storyRing?: 'unseen' | 'seen'
  className?: string
}

export function Avatar({ src, alt, size = 32, storyRing, className }: AvatarProps) {
  const image = src ? (
    <img src={src} alt={alt} />
  ) : (
    <Icon name="profile" size={Math.round(size * 0.6)} />
  )

  if (storyRing) {
    const ringSize = size + 8
    return (
      <div
        className={[styles.avatar, styles.ring, storyRing === 'seen' ? styles.ringSeen : ''].join(' ')}
        style={{ width: ringSize, height: ringSize }}
      >
        <div className={styles.ringInner}>
          <div className={styles.avatar} style={{ width: '100%', height: '100%' }}>
            {image}
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className={[styles.avatar, className].filter(Boolean).join(' ')} style={{ width: size, height: size }}>
      {image}
    </div>
  )
}
