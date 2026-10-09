import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { InfiniteGrid } from '@/components/InfiniteGrid'
import { PostGridTile } from '@/components/PostGridTile'
import * as hashtagsApi from '@/lib/api/endpoints/hashtags'
import { useCursorInfiniteQuery } from '@/lib/hooks/useCursorInfiniteQuery'
import { queryKeys } from '@/lib/queryKeys'
import styles from './HashtagPage.module.css'

/** Grid of public posts carrying a hashtag, newest first — reached from any #tag in a caption or from search. */
export function HashtagPage() {
  const { tag = '' } = useParams<{ tag: string }>()
  const normalized = tag.toLowerCase()

  const { data: summary } = useQuery({
    queryKey: queryKeys.hashtag(normalized),
    queryFn: () => hashtagsApi.getHashtag(normalized),
    enabled: Boolean(normalized),
  })
  const postsQuery = useCursorInfiniteQuery(
    queryKeys.hashtagPosts(normalized),
    (cursor) => hashtagsApi.getHashtagPosts(normalized, cursor),
    { enabled: Boolean(normalized) },
  )

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>#{normalized}</h1>
        {summary ? (
          <p className={styles.count}>
            {summary.postCount} {summary.postCount === 1 ? 'post' : 'posts'}
          </p>
        ) : null}
      </header>
      <InfiniteGrid
        items={postsQuery.items}
        isLoading={postsQuery.isLoading}
        hasNextPage={postsQuery.hasNextPage}
        isFetchingNextPage={postsQuery.isFetchingNextPage}
        fetchNextPage={() => postsQuery.fetchNextPage()}
        keyFor={(post) => post.id}
        renderTile={(post) => <PostGridTile post={post} />}
        emptyState={<p>No posts yet for this hashtag.</p>}
      />
    </div>
  )
}
