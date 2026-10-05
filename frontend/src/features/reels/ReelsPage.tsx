import * as reelsApi from '@/lib/api/endpoints/reels'
import { useCursorInfiniteQuery } from '@/lib/hooks/useCursorInfiniteQuery'
import { useInfiniteScrollSentinel } from '@/lib/hooks/useInfiniteScrollSentinel'
import { queryKeys } from '@/lib/queryKeys'
import { ReelItem } from './ReelItem'
import styles from './ReelsPage.module.css'

export function ReelsPage() {
  const { items, isLoading, hasNextPage, fetchNextPage, isFetchingNextPage } = useCursorInfiniteQuery(
    queryKeys.reelsFeed(),
    reelsApi.getReelsFeed,
  )
  const sentinelRef = useInfiniteScrollSentinel(() => fetchNextPage(), Boolean(hasNextPage) && !isFetchingNextPage)

  if (!isLoading && items.length === 0) {
    return (
      <div className={styles.container}>
        <div className={styles.empty}>No reels yet. Follow some accounts to see their reels here.</div>
      </div>
    )
  }

  return (
    <div className={styles.container}>
      {items.map((post) => (
        <ReelItem key={post.id} post={post} />
      ))}
      <div ref={sentinelRef} />
    </div>
  )
}
