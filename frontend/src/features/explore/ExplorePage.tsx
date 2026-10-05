import { InfiniteGrid } from '@/components/InfiniteGrid'
import { PostGridTile } from '@/components/PostGridTile'
import * as feedApi from '@/lib/api/endpoints/feed'
import { useCursorInfiniteQuery } from '@/lib/hooks/useCursorInfiniteQuery'
import { queryKeys } from '@/lib/queryKeys'
import styles from './ExplorePage.module.css'

export function ExplorePage() {
  const query = useCursorInfiniteQuery(queryKeys.explore(), feedApi.getExploreFeed)

  return (
    <div className={styles.page}>
      <InfiniteGrid
        items={query.items}
        isLoading={query.isLoading}
        hasNextPage={query.hasNextPage}
        isFetchingNextPage={query.isFetchingNextPage}
        fetchNextPage={() => query.fetchNextPage()}
        keyFor={(post) => post.id}
        renderTile={(post) => <PostGridTile post={post} />}
        emptyState={<p>Nothing to explore yet.</p>}
      />
    </div>
  )
}
