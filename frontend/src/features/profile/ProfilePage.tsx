import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { Icon } from '@/components/Icon'
import { InfiniteGrid } from '@/components/InfiniteGrid'
import { PostGridTile } from '@/components/PostGridTile'
import { CreateHighlightModal } from '@/features/highlights/CreateHighlightModal'
import { HighlightViewer } from '@/features/highlights/HighlightViewer'
import { NewHighlightFlow } from '@/features/highlights/NewHighlightFlow'
import * as highlightsApi from '@/lib/api/endpoints/highlights'
import * as postsApi from '@/lib/api/endpoints/posts'
import * as savedApi from '@/lib/api/endpoints/saved'
import * as usersApi from '@/lib/api/endpoints/users'
import { useCursorInfiniteQuery } from '@/lib/hooks/useCursorInfiniteQuery'
import { queryKeys } from '@/lib/queryKeys'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { ProfileHeader } from './ProfileHeader'
import styles from './ProfilePage.module.css'

type Tab = 'posts' | 'saved'

export function ProfilePage() {
  const { username } = useParams<{ username: string }>()
  const [tab, setTab] = useState<Tab>('posts')
  const [viewingHighlightId, setViewingHighlightId] = useState<number | null>(null)
  const [showNewHighlight, setShowNewHighlight] = useState(false)
  const [creatingHighlightStoryId, setCreatingHighlightStoryId] = useState<number | null>(null)

  const { data: profile, isLoading, isError } = useQuery({
    queryKey: queryKeys.userProfile(username ?? ''),
    queryFn: () => usersApi.getProfile(username!),
    enabled: Boolean(username),
  })

  const isSelf = profile?.viewerRelationship === 'SELF'
  const isGated = Boolean(profile?.isPrivate) && profile?.viewerRelationship !== 'FOLLOWING' && !isSelf
  const activeTab = isSelf ? tab : 'posts'

  const { data: highlights } = useQuery({
    queryKey: queryKeys.highlights(username ?? ''),
    queryFn: () => highlightsApi.getHighlights(username!),
    enabled: Boolean(username) && !isGated,
  })

  const postsQuery = useCursorInfiniteQuery(
    queryKeys.userPosts(username ?? ''),
    (cursor) => postsApi.getUserPosts(username!, cursor),
    { enabled: Boolean(username) && !isGated && activeTab === 'posts' },
  )
  const savedQuery = useCursorInfiniteQuery(queryKeys.savedPosts(), (cursor) => savedApi.getSavedPosts(cursor), {
    enabled: isSelf && activeTab === 'saved',
  })

  // A route like /:username that swallows any single-segment path (matching real Instagram's own
  // URL scheme, where usernames are the top-level namespace) needs its own "not found" handling —
  // without this, a typo'd or deleted username silently rendered a blank page forever instead of a
  // clear message, since the query settles into an error state that nothing was checking for.
  if (isError) {
    return <NotFoundPage />
  }

  if (isLoading || !profile) {
    return null
  }

  const activeQuery = activeTab === 'saved' ? savedQuery : postsQuery

  return (
    <div className={styles.page}>
      <ProfileHeader profile={profile} />
      {isSelf || (highlights && highlights.length > 0) ? (
        <div className={styles.highlightsRow}>
          {isSelf ? (
            <button type="button" className={styles.highlightCircle} onClick={() => setShowNewHighlight(true)}>
              <span className={styles.highlightNewRing}>
                <Icon name="create" size={20} />
              </span>
              <span className={styles.highlightLabel}>New</span>
            </button>
          ) : null}
          {(highlights ?? []).map((h) => (
            <button
              type="button"
              key={h.id}
              className={styles.highlightCircle}
              onClick={() => setViewingHighlightId(h.id)}
            >
              <span className={styles.highlightRing}>
                {h.coverUrl ? (
                  <img src={h.coverUrl} alt={h.title} className={styles.highlightCover} />
                ) : (
                  <Icon name="reels" size={20} />
                )}
              </span>
              <span className={styles.highlightLabel}>{h.title}</span>
            </button>
          ))}
        </div>
      ) : null}
      <div className={styles.tabBar}>
        <button
          type="button"
          className={[styles.tab, activeTab === 'posts' ? styles.tabActive : ''].join(' ')}
          onClick={() => setTab('posts')}
        >
          <Icon name="reels" size={12} />
          POSTS
        </button>
        {isSelf ? (
          <button
            type="button"
            className={[styles.tab, activeTab === 'saved' ? styles.tabActive : ''].join(' ')}
            onClick={() => setTab('saved')}
          >
            <Icon name="bookmark" size={12} />
            SAVED
          </button>
        ) : null}
      </div>
      {isGated ? (
        <div className={styles.privateGate}>
          <Icon name="lock" size={48} className={styles.privateGateIcon} />
          <p className={styles.privateGateTitle}>This Account is Private</p>
          <p className={styles.privateGateSubtitle}>Follow this account to see their photos and videos.</p>
        </div>
      ) : (
        <div className={styles.gridWrapper}>
          <InfiniteGrid
            items={activeQuery.items}
            isLoading={activeQuery.isLoading}
            hasNextPage={activeQuery.hasNextPage}
            isFetchingNextPage={activeQuery.isFetchingNextPage}
            fetchNextPage={() => activeQuery.fetchNextPage()}
            keyFor={(post) => post.id}
            renderTile={(post) => <PostGridTile post={post} />}
            emptyState={<p>{activeTab === 'saved' ? 'No saved posts yet.' : 'No posts yet.'}</p>}
          />
        </div>
      )}
      {viewingHighlightId != null ? (
        <HighlightViewer highlightId={viewingHighlightId} isOwn={isSelf} onClose={() => setViewingHighlightId(null)} />
      ) : null}
      {showNewHighlight && username ? (
        <NewHighlightFlow
          username={username}
          onClose={() => setShowNewHighlight(false)}
          onStorySelected={(storyId) => {
            setShowNewHighlight(false)
            setCreatingHighlightStoryId(storyId)
          }}
        />
      ) : null}
      {creatingHighlightStoryId != null ? (
        <CreateHighlightModal storyId={creatingHighlightStoryId} onClose={() => setCreatingHighlightStoryId(null)} />
      ) : null}
    </div>
  )
}
