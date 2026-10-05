import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Avatar } from '@/components/Avatar'
import { Icon } from '@/components/Icon'
import { useAuth } from '@/contexts/useAuth'
import * as storiesApi from '@/lib/api/endpoints/stories'
import { CreateStoryModal } from '@/features/stories/CreateStoryModal'
import { StoryViewer } from '@/features/stories/StoryViewer'
import { useStoriesFeed } from '@/features/stories/useStoriesFeed'
import { useInfiniteScrollSentinel } from '@/lib/hooks/useInfiniteScrollSentinel'
import { queryKeys } from '@/lib/queryKeys'
import styles from './StoriesTray.module.css'

export function StoriesTray() {
  const { user } = useAuth()
  const { groups: otherGroups, hasNextPage, fetchNextPage, isFetchingNextPage } = useStoriesFeed()
  const sentinelRef = useInfiniteScrollSentinel(() => fetchNextPage(), Boolean(hasNextPage) && !isFetchingNextPage)
  const [viewerGroupIndex, setViewerGroupIndex] = useState<number | null>(null)
  const [createOpen, setCreateOpen] = useState(false)

  // /stories/feed is follows-based (mirrors the home feed's own "never includes your own posts"
  // rule) — it can never tell us whether the viewer has an active story, so that needs its own
  // per-user lookup instead of being derived from the aggregate feed.
  const ownStoriesQuery = useQuery({
    queryKey: queryKeys.userStories(user?.username ?? ''),
    queryFn: () => storiesApi.getUserStories(user!.username),
    enabled: Boolean(user),
  })
  const ownStories = ownStoriesQuery.data ?? []
  const hasOwnStory = ownStories.length > 0

  const groups = hasOwnStory && user ? [{ author: user, stories: ownStories }, ...otherGroups] : otherGroups

  function handleOwnClick() {
    if (hasOwnStory) {
      setViewerGroupIndex(0)
    } else {
      setCreateOpen(true)
    }
  }

  if (otherGroups.length === 0 && !user) {
    return null
  }

  return (
    <div className={styles.tray}>
      {user ? (
        <div className={styles.item}>
          <button type="button" className={styles.ownButton} onClick={handleOwnClick}>
            <Avatar src={user.profilePictureUrl} alt={user.username} size={56} storyRing={hasOwnStory ? 'unseen' : undefined} />
            {!hasOwnStory ? (
              <span className={styles.addBadge}>
                <Icon name="create" size={14} />
              </span>
            ) : null}
          </button>
          <span className={styles.username}>Your story</span>
        </div>
      ) : null}
      {otherGroups.map((group) => (
        <div key={group.author.id} className={styles.item}>
          <button
            type="button"
            onClick={() => setViewerGroupIndex(groups.findIndex((g) => g.author.id === group.author.id))}
          >
            <Avatar
              src={group.author.profilePictureUrl}
              alt={group.author.username}
              size={56}
              storyRing={group.stories.every((s) => s.seenByViewer) ? 'seen' : 'unseen'}
            />
          </button>
          <span className={styles.username}>{group.author.username}</span>
        </div>
      ))}
      <div ref={sentinelRef} className={styles.sentinel} />

      {viewerGroupIndex !== null ? (
        <StoryViewer groups={groups} initialGroupIndex={viewerGroupIndex} onClose={() => setViewerGroupIndex(null)} />
      ) : null}
      {createOpen ? <CreateStoryModal onClose={() => setCreateOpen(false)} /> : null}
    </div>
  )
}
