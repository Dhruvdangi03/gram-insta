import type { ReactNode } from 'react'
import { useInfiniteScrollSentinel } from '@/lib/hooks/useInfiniteScrollSentinel'
import styles from './InfiniteGrid.module.css'

export interface InfiniteGridProps<T> {
  items: T[]
  isLoading: boolean
  hasNextPage?: boolean
  isFetchingNextPage: boolean
  fetchNextPage: () => void
  renderTile: (item: T) => ReactNode
  keyFor: (item: T) => string | number
  emptyState?: ReactNode
}

/** The 3-column square grid shared by profile posts and explore — one pagination + layout implementation for both. */
export function InfiniteGrid<T>({
  items,
  isLoading,
  hasNextPage,
  isFetchingNextPage,
  fetchNextPage,
  renderTile,
  keyFor,
  emptyState,
}: InfiniteGridProps<T>) {
  const sentinelRef = useInfiniteScrollSentinel(() => fetchNextPage(), Boolean(hasNextPage) && !isFetchingNextPage)

  if (isLoading) {
    return (
      <div className={styles.grid}>
        {Array.from({ length: 9 }).map((_, i) => (
          <div key={i} className={styles.skeletonTile} />
        ))}
      </div>
    )
  }

  if (items.length === 0 && emptyState) {
    return <div className={styles.grid}>{emptyState}</div>
  }

  return (
    <div className={styles.grid}>
      {items.map((item) => (
        <div key={keyFor(item)}>{renderTile(item)}</div>
      ))}
      <div ref={sentinelRef} className={styles.sentinel} />
    </div>
  )
}
