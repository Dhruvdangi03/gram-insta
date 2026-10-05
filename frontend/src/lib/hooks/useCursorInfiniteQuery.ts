import { useInfiniteQuery, type QueryKey } from '@tanstack/react-query'
import type { CursorPage } from '@/lib/api/types'

/**
 * Wraps every CursorPage<T> list endpoint with the same useInfiniteQuery shape — one pagination
 * implementation shared by feed, profile grid, explore, hashtag grid, followers/following,
 * notifications, and the conversation list, instead of eight ad-hoc copies.
 */
export function useCursorInfiniteQuery<T>(
  queryKey: QueryKey,
  fetchPage: (cursor?: string) => Promise<CursorPage<T>>,
  options?: { enabled?: boolean },
) {
  const query = useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) => fetchPage(pageParam as string | undefined),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => (lastPage.hasMore ? lastPage.nextCursor ?? undefined : undefined),
    enabled: options?.enabled,
  })

  const items = query.data?.pages.flatMap((page) => page.items) ?? []

  return { ...query, items }
}
