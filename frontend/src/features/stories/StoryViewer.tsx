import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Avatar } from '@/components/Avatar'
import { Icon } from '@/components/Icon'
import { useAuth } from '@/contexts/useAuth'
import { AddToHighlightSheet } from '@/features/highlights/AddToHighlightSheet'
import { CreateHighlightModal } from '@/features/highlights/CreateHighlightModal'
import * as storiesApi from '@/lib/api/endpoints/stories'
import { formatRelativeTime } from '@/lib/formatters/relativeTime'
import { queryKeys } from '@/lib/queryKeys'
import type { AuthorStories } from './useStoriesFeed'
import styles from './StoryViewer.module.css'

export interface StoryViewerProps {
  groups: AuthorStories[]
  initialGroupIndex: number
  onClose: () => void
}

export function StoryViewer({ groups, initialGroupIndex, onClose }: StoryViewerProps) {
  const [groupIndex, setGroupIndex] = useState(initialGroupIndex)
  const [storyIndex, setStoryIndex] = useState(0)
  const [showAddToHighlight, setShowAddToHighlight] = useState(false)
  const [creatingHighlightForStory, setCreatingHighlightForStory] = useState(false)
  const { user } = useAuth()
  const queryClient = useQueryClient()

  const group = groups[groupIndex]
  const story = group?.stories[storyIndex]

  // groupIndex/storyIndex are plain local state, not reconciled against `groups` when it
  // refetches in the background (e.g. the stories feed going stale while this stays open) — if
  // they ever point past the live data, close instead of leaving a dead, un-closeable black
  // overlay on screen.
  useEffect(() => {
    if (!group || !story) {
      onClose()
    }
  }, [group, story, onClose])

  const { mutate: markViewed } = useMutation({
    mutationFn: (id: number) => storiesApi.markStoryViewed(id),
  })
  const storyId = story?.id
  const storyAlreadySeen = story?.seenByViewer ?? true

  useEffect(() => {
    if (storyId !== undefined && !storyAlreadySeen) {
      markViewed(storyId)
    }
  }, [storyId, storyAlreadySeen, markViewed])

  const deleteMutation = useMutation({
    mutationFn: (id: number) => storiesApi.deleteStory(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.storiesFeed() })
      if (user) queryClient.invalidateQueries({ queryKey: queryKeys.userStories(user.username) })
      // Advance to whatever's next (another story in this group, the next group, or close if
      // nothing remains) instead of unconditionally closing and losing the viewer's place.
      goToNextStory()
    },
  })

  function goToNextStory() {
    if (!group) return
    if (storyIndex < group.stories.length - 1) {
      setStoryIndex(storyIndex + 1)
    } else if (groupIndex < groups.length - 1) {
      setGroupIndex(groupIndex + 1)
      setStoryIndex(0)
    } else {
      onClose()
    }
  }

  function goToPreviousStory() {
    if (storyIndex > 0) {
      setStoryIndex(storyIndex - 1)
    } else if (groupIndex > 0) {
      setStoryIndex(groups[groupIndex - 1].stories.length - 1)
      setGroupIndex(groupIndex - 1)
    }
  }

  if (!group || !story) return null

  const isOwn = story.author.id === user?.id

  return (
    <div className={styles.overlay}>
      <div className={styles.frame}>
        <div className={styles.progressRow}>
          {group.stories.map((s, i) => (
            <div key={s.id} className={styles.segment}>
              <div
                className={[
                  styles.segmentFill,
                  i === storyIndex ? styles.segmentFillActive : '',
                  i < storyIndex ? styles.segmentFillDone : '',
                ].join(' ')}
                onAnimationEnd={i === storyIndex ? goToNextStory : undefined}
              />
            </div>
          ))}
        </div>
        <header className={styles.header}>
          <Avatar src={story.author.profilePictureUrl} alt={story.author.username} size={32} />
          <span className={styles.username}>{story.author.username}</span>
          <span className={styles.timestamp}>{formatRelativeTime(story.createdAt)}</span>
          <div className={styles.headerActions}>
            {isOwn ? (
              <button
                type="button"
                className={styles.iconButton}
                onClick={() => setShowAddToHighlight(true)}
                aria-label="Add to highlight"
              >
                <Icon name="create" />
              </button>
            ) : null}
            {isOwn ? (
              <button type="button" className={styles.iconButton} onClick={() => deleteMutation.mutate(story.id)} aria-label="Delete story">
                <Icon name="trash" />
              </button>
            ) : null}
            <button type="button" className={styles.iconButton} onClick={onClose} aria-label="Close">
              <Icon name="close" />
            </button>
          </div>
        </header>
        <img className={styles.media} src={story.mediaUrl} alt={`${story.author.username}'s story`} />
        <button type="button" className={[styles.navZone, styles.navZoneLeft].join(' ')} onClick={goToPreviousStory} aria-label="Previous story" />
        <button type="button" className={[styles.navZone, styles.navZoneRight].join(' ')} onClick={goToNextStory} aria-label="Next story" />
      </div>
      {showAddToHighlight ? (
        <AddToHighlightSheet
          storyId={story.id}
          username={story.author.username}
          onClose={() => setShowAddToHighlight(false)}
          onCreateNew={() => {
            setShowAddToHighlight(false)
            setCreatingHighlightForStory(true)
          }}
        />
      ) : null}
      {creatingHighlightForStory ? (
        <CreateHighlightModal storyId={story.id} onClose={() => setCreatingHighlightForStory(false)} />
      ) : null}
    </div>
  )
}
