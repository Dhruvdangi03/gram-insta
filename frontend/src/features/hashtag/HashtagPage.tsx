import { useParams } from 'react-router-dom'
import { InfiniteGrid } from '@/components/InfiniteGrid'
import { PostGridTile } from '@/components/PostGridTile'
import * as hashtagsApi from '@/lib/api/endpoints/hashtags'
import { useCursorInfiniteQuery } from '@/lib/hooks/useCursorInfiniteQuery'
import { queryKeys } from '@/lib/queryKeys'
import styles from './HashtagPage.module.css'

export function HashtagPage() {
  const { tag } = useParams<{ tag: string }>()
  const normalizedTag = (tag ?? '').toLowerCase()

  const query = useCursorInfiniteQuery(
    queryKeys.hashtagPosts(normalizedTag),
    (cursor) => hashtagsApi.getPostsByHashtag(normalizedTag, cursor),
    { enabled: Boolean(normalizedTag) },
  )

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={styles.iconCircle}>#</div>
        <div>
          <h1 className={styles.title}>#{normalizedTag}</h1>
          <p className={styles.subtitle}>Posts</p>
        </div>
      </header>
      <InfiniteGrid
        items={query.items}
        isLoading={query.isLoading}
        hasNextPage={query.hasNextPage}
        isFetchingNextPage={query.isFetchingNextPage}
        fetchNextPage={() => query.fetchNextPage()}
        keyFor={(post) => post.id}
        renderTile={(post) => <PostGridTile post={post} />}
        emptyState={<p>No posts yet for #{normalizedTag}.</p>}
      />
    </div>
  )
}
