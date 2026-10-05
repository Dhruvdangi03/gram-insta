import { useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Icon, type IconName } from '@/components/Icon'
import { Wordmark } from '@/components/Wordmark'
import { useAuth } from '@/contexts/useAuth'
import { NotificationBadge } from '@/features/notifications/NotificationBadge'
import { SearchPanel } from '@/features/search/SearchPanel'
import styles from './MobileTabBar.module.css'

interface Tab {
  to: string
  icon: IconName
  end?: boolean
  /** Opens as a modal over the current page instead of navigating away — see the Phase 5 plan's background-location pattern. */
  asModal?: boolean
}

const TABS: Tab[] = [
  { to: '/', icon: 'home', end: true },
  { to: '/explore', icon: 'explore' },
  { to: '/create', icon: 'create', asModal: true },
  { to: '/reels', icon: 'reels' },
]

export function MobileTopBar() {
  // Sidebar (desktop-only, display:none on mobile) is otherwise the only way to reach Search —
  // a real gap caught during the mobile pass: Search was completely unreachable below 735px.
  const [searchOpen, setSearchOpen] = useState(false)

  return (
    <header className={styles.topbar}>
      <Wordmark />
      <div className={styles.topbarActions}>
        <button type="button" className={styles.tab} onClick={() => setSearchOpen(true)} aria-label="Search">
          <Icon name="search" />
        </button>
        <NavLink to="/notifications" className={styles.tab} aria-label="Notifications">
          <NotificationBadge>
            <Icon name="heart" />
          </NotificationBadge>
        </NavLink>
        <NavLink to="/direct/inbox" className={styles.tab} aria-label="Messages">
          <Icon name="messages" />
        </NavLink>
      </div>
      {searchOpen ? <SearchPanel onClose={() => setSearchOpen(false)} /> : null}
    </header>
  )
}

export function MobileTabBar() {
  const { user } = useAuth()
  const location = useLocation()

  return (
    <nav className={styles.tabbar}>
      {TABS.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          end={tab.end}
          state={tab.asModal ? { backgroundLocation: location } : undefined}
          className={styles.tab}
        >
          {({ isActive }) => <Icon name={tab.icon} variant={isActive ? 'filled' : 'outline'} />}
        </NavLink>
      ))}
      {user ? (
        <NavLink to={`/${user.username}`} className={styles.tab}>
          <Avatar src={user.profilePictureUrl} alt={user.username} size={24} />
        </NavLink>
      ) : null}
    </nav>
  )
}
