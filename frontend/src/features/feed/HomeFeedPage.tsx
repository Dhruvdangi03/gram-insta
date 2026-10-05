import { PostCard } from '@/components/PostCard'
import * as feedApi from '@/lib/api/endpoints/feed'
import { useCursorInfiniteQuery } from '@/lib/hooks/useCursorInfiniteQuery'
import { useInfiniteScrollSentinel } from '@/lib/hooks/useInfiniteScrollSentinel'
import { queryKeys } from '@/lib/queryKeys'
import { StoriesTray } from './StoriesTray'
import { SuggestionsSidebar } from './SuggestionsSidebar'
import styles from './HomeFeedPage.module.css'

export function HomeFeedPage() {
  const { items, isLoading, hasNextPage, fetchNextPage, isFetchingNextPage } = useCursorInfiniteQuery(
    queryKeys.feed(),
    feedApi.getHomeFeed,
  )
  const sentinelRef = useInfiniteScrollSentinel(() => fetchNextPage(), Boolean(hasNextPage) && !isFetchingNextPage)

  return (
    <div className={styles.page}>
      <div className={styles.column}>
        <StoriesTray />
        {isLoading ? (
          <>
            <div className={styles.skeleton} />
            <div className={styles.skeleton} />
          </>
        ) : items.length === 0 ? (
          <div className={styles.emptyState}>
            <p>No posts yet. Follow some accounts to see their photos and videos here.</p>
          </div>
        ) : (
          items.map((post) => <PostCard key={post.id} post={post} />)
        )}
        <div ref={sentinelRef} className={styles.sentinel} />
      </div>
      <SuggestionsSidebar />
    </div>
  )
}
