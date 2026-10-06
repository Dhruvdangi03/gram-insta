import type { QueryClient } from '@tanstack/react-query'
import type { Post } from './api/types'

function isCursorPage(value: unknown): value is { items: unknown[] } {
  return typeof value === 'object' && value !== null && Array.isArray((value as { items?: unknown }).items)
}

function isInfiniteData(value: unknown): value is { pages: unknown[] } {
  return typeof value === 'object' && value !== null && Array.isArray((value as { pages?: unknown }).pages)
}

// savedByViewer only exists on Post — comments also carry likeCount, so likeCount alone isn't
// enough to tell a cached post from a cached comment.
function isPost(value: unknown): value is Post {
  return typeof value === 'object' && value !== null && 'likeCount' in value && 'savedByViewer' in value
}

/**
 * A post's like/comment state is cached independently in N places at once (home feed, explore,
 * a profile grid, a single-post detail view) — this patches all of them via a
 * predicate over every query's cached shape rather than a fixed list of query keys, so a like/
 * comment mutation is instantly reflected everywhere the post happens to be cached. Deliberately
 * does not force a refetch afterwards (that would reshuffle an in-progress scroll).
 */
export function patchPostInAllCaches(queryClient: QueryClient, postId: number, patch: Partial<Post>) {
  // Checked with isPost, not just by id — comments, notifications, conversations, messages, and
  // followers/following all use this same {items:[...]}/{pages:[...]} shape, and every entity
  // table has its own independent id sequence starting at 1, so a comment or notification can
  // easily share a numeric id with some post. Without the type guard, patching post #5 would also
  // spread Post fields onto an unrelated cached comment/notification #5.
  const applyToItem = (item: unknown): unknown => (isPost(item) && item.id === postId ? { ...item, ...patch } : item)

  queryClient.setQueriesData({ predicate: () => true }, (data: unknown) => {
    if (isInfiniteData(data)) {
      return {
        ...data,
        pages: data.pages.map((page) => (isCursorPage(page) ? { ...page, items: page.items.map(applyToItem) } : page)),
      }
    }
    if (isCursorPage(data)) {
      return { ...data, items: data.items.map(applyToItem) }
    }
    if (isPost(data) && data.id === postId) {
      return { ...data, ...patch }
    }
    return data
  })
}

/** Same predicate-over-every-cached-shape approach as patchPostInAllCaches, but removes the post
 * instead of patching it — used after a successful delete so it disappears from feed/grid/detail
 * caches immediately without a forced refetch. Also checked with isPost for the same cross-entity
 * id-collision reason patchPostInAllCaches is — otherwise this could silently drop an unrelated
 * cached comment or notification that happens to share the deleted post's numeric id. */
export function removePostFromAllCaches(queryClient: QueryClient, postId: number) {
  const keepItem = (item: unknown) => !(isPost(item) && item.id === postId)

  queryClient.setQueriesData({ predicate: () => true }, (data: unknown) => {
    if (isInfiniteData(data)) {
      return {
        ...data,
        pages: data.pages.map((page) => (isCursorPage(page) ? { ...page, items: page.items.filter(keepItem) } : page)),
      }
    }
    if (isCursorPage(data)) {
      return { ...data, items: data.items.filter(keepItem) }
    }
    return data
  })
}
