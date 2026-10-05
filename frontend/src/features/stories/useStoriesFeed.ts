import * as storiesApi from '@/lib/api/endpoints/stories'
import type { Story, UserSummary } from '@/lib/api/types'
import { useCursorInfiniteQuery } from '@/lib/hooks/useCursorInfiniteQuery'
import { queryKeys } from '@/lib/queryKeys'

export interface AuthorStories {
  author: UserSummary
  stories: Story[]
}

/** Groups the flat /stories/feed list into one entry per author, in first-seen order — the tray
 * shows one ring per author, and the viewer steps through that author's stories in sequence.
 * The feed itself stays newest-activity-first (for cursor pagination and tray ordering), but each
 * author's own stories are reversed to chronological order so playback tells them oldest-first. */
export function useStoriesFeed() {
  const query = useCursorInfiniteQuery(queryKeys.storiesFeed(), (cursor) => storiesApi.getStoriesFeed(cursor))

  const grouped: AuthorStories[] = []
  const indexByAuthor = new Map<number, number>()
  for (const story of query.items) {
    const existingIndex = indexByAuthor.get(story.author.id)
    if (existingIndex === undefined) {
      indexByAuthor.set(story.author.id, grouped.length)
      grouped.push({ author: story.author, stories: [story] })
    } else {
      grouped[existingIndex].stories.push(story)
    }
  }
  for (const group of grouped) {
    group.stories.reverse()
  }

  return { ...query, groups: grouped }
}
