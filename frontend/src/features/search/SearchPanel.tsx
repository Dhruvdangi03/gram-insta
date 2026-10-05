import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Icon } from '@/components/Icon'
import { VerifiedBadge } from '@/components/VerifiedBadge'
import * as searchApi from '@/lib/api/endpoints/search'
import type { UserSearchResult } from '@/lib/api/types'
import { useDebouncedValue } from '@/lib/hooks/useDebouncedValue'
import { queryKeys } from '@/lib/queryKeys'
import { addRecentSearch, clearRecentSearches, getRecentSearches } from './recentSearches'
import styles from './SearchPanel.module.css'

export function SearchPanel({ onClose }: { onClose: () => void }) {
  const [query, setQuery] = useState('')
  const [recent, setRecent] = useState(() => getRecentSearches())
  const debouncedQuery = useDebouncedValue(query.trim(), 300)
  const inputRef = useRef<HTMLInputElement | null>(null)

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  const usersQuery = useQuery({
    queryKey: queryKeys.searchUsers(debouncedQuery),
    queryFn: () => searchApi.searchUsers(debouncedQuery),
    enabled: debouncedQuery.length > 0,
  })
  const postsQuery = useQuery({
    queryKey: queryKeys.searchPosts(debouncedQuery),
    queryFn: () => searchApi.searchPosts(debouncedQuery),
    enabled: debouncedQuery.length > 0,
  })

  function recordVisit(user: UserSearchResult) {
    addRecentSearch(user)
    setRecent(getRecentSearches())
    onClose()
  }

  const isSearching = debouncedQuery.length > 0

  return (
    <>
      <div className={styles.backdrop} onClick={onClose} />
      <div className={styles.panel} role="dialog" aria-label="Search">
        <div className={styles.header}>
          <h2 className={styles.title}>Search</h2>
          <div className={styles.inputWrapper}>
            <Icon name="search" size={16} />
            <input
              ref={inputRef}
              className={styles.input}
              placeholder="Search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
            {query ? (
              <button type="button" className={styles.clearButton} onClick={() => setQuery('')} aria-label="Clear">
                <Icon name="close" size={14} />
              </button>
            ) : null}
          </div>
        </div>

        <div className={styles.body}>
          {!isSearching ? (
            <>
              <div className={styles.sectionHeader}>
                <span>Recent</span>
                {recent.length > 0 ? (
                  <button
                    type="button"
                    className={styles.clearAll}
                    onClick={() => {
                      clearRecentSearches()
                      setRecent([])
                    }}
                  >
                    Clear all
                  </button>
                ) : null}
              </div>
              {recent.length === 0 ? (
                <p className={styles.empty}>No recent searches.</p>
              ) : (
                recent.map((user) => (
                  <Link key={user.id} to={`/${user.username}`} className={styles.row} onClick={() => recordVisit(user)}>
                    <Avatar src={user.profilePictureUrl} alt={user.username} size={44} />
                    <div className={styles.rowText}>
                      <span className={styles.username}>
                        {user.username}
                        {user.isVerified ? <VerifiedBadge size={12} /> : null}
                      </span>
                      <span className={styles.fullName}>{user.fullName}</span>
                    </div>
                  </Link>
                ))
              )}
            </>
          ) : (
            <>
              <div className={styles.sectionHeader}>
                <span>Accounts</span>
              </div>
              {usersQuery.data?.length === 0 ? <p className={styles.empty}>No accounts found.</p> : null}
              {usersQuery.data?.map((user) => (
                <Link key={user.id} to={`/${user.username}`} className={styles.row} onClick={() => recordVisit(user)}>
                  <Avatar src={user.profilePictureUrl} alt={user.username} size={44} />
                  <div className={styles.rowText}>
                    <span className={styles.username}>
                      {user.username}
                      {user.isVerified ? <VerifiedBadge size={12} /> : null}
                    </span>
                    <span className={styles.fullName}>{user.fullName}</span>
                  </div>
                </Link>
              ))}

              {postsQuery.data && postsQuery.data.length > 0 ? (
                <>
                  <div className={styles.sectionHeader}>
                    <span>Posts</span>
                  </div>
                  {postsQuery.data.map((post) => (
                    <Link key={post.id} to={`/p/${post.id}`} className={styles.postRow} onClick={onClose}>
                      <span className={styles.username}>{post.authorUsername}</span>{' '}
                      <span className={styles.postCaption}>{post.caption}</span>
                    </Link>
                  ))}
                </>
              ) : null}
            </>
          )}
        </div>
      </div>
    </>
  )
}
