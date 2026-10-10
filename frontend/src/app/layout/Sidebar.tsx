import { useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Icon, type IconName } from '@/components/Icon'
import { Wordmark } from '@/components/Wordmark'
import { useAuth } from '@/contexts/useAuth'
import { MessagesBadge } from '@/features/messaging/MessagesBadge'
import { NotificationBadge } from '@/features/notifications/NotificationBadge'
import { SearchPanel } from '@/features/search/SearchPanel'
import styles from './Sidebar.module.css'

interface NavItem {
  to: string
  icon: IconName
  label: string
  end?: boolean
}

const NAV_ITEMS: NavItem[] = [
  { to: '/', icon: 'home', label: 'Home', end: true },
  { to: '/reels', icon: 'reels', label: 'Reels' },
  { to: '/direct/inbox', icon: 'messages', label: 'Messages' },
]

export function Sidebar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchOpen, setSearchOpen] = useState(false)

  return (
    <aside className={styles.sidebar}>
      <NavLink to="/" className={styles.logo} aria-label="Instaclone">
        <Icon name="home" className={styles.logoMark} />
        <Wordmark className={styles.logoWord} />
      </NavLink>
      <nav className={styles.nav}>
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) => [styles.navItem, isActive ? styles.navItemActive : ''].join(' ')}
          >
            {({ isActive }: { isActive: boolean }) => (
              <>
                {item.to === '/direct/inbox' ? (
                  <MessagesBadge>
                    <Icon name={item.icon} variant={isActive ? 'filled' : 'outline'} />
                  </MessagesBadge>
                ) : (
                  <Icon name={item.icon} variant={isActive ? 'filled' : 'outline'} />
                )}
                <span className={styles.navLabel}>{item.label}</span>
              </>
            )}
          </NavLink>
        ))}
        <button
          type="button"
          className={[styles.navItem, searchOpen ? styles.navItemActive : ''].join(' ')}
          onClick={() => setSearchOpen((open) => !open)}
        >
          <Icon name="search" />
          <span className={styles.navLabel}>Search</span>
        </button>
        <NavLink
          to="/explore"
          className={({ isActive }) => [styles.navItem, isActive ? styles.navItemActive : ''].join(' ')}
        >
          {({ isActive }: { isActive: boolean }) => (
            <>
              <Icon name="explore" variant={isActive ? 'filled' : 'outline'} />
              <span className={styles.navLabel}>Explore</span>
            </>
          )}
        </NavLink>
        <NavLink
          to="/notifications"
          className={({ isActive }) => [styles.navItem, isActive ? styles.navItemActive : ''].join(' ')}
        >
          {({ isActive }: { isActive: boolean }) => (
            <>
              <NotificationBadge>
                <Icon name="heart" variant={isActive ? 'filled' : 'outline'} />
              </NotificationBadge>
              <span className={styles.navLabel}>Notifications</span>
            </>
          )}
        </NavLink>
        <button
          type="button"
          className={styles.navItem}
          onClick={() => navigate('/create', { state: { backgroundLocation: location } })}
        >
          <Icon name="create" />
          <span className={styles.navLabel}>Create</span>
        </button>
        {user ? (
          <NavLink
            to={`/${user.username}`}
            className={({ isActive }) => [styles.navItem, isActive ? styles.navItemActive : ''].join(' ')}
          >
            <Avatar src={user.profilePictureUrl} alt={user.username} size={24} />
            <span className={styles.navLabel}>Profile</span>
          </NavLink>
        ) : null}
      </nav>
      <button type="button" className={[styles.navItem, styles.more].join(' ')} onClick={() => void logout()}>
        <Icon name="more" />
        <span className={styles.navLabel}>Log out</span>
      </button>
      {searchOpen ? <SearchPanel onClose={() => setSearchOpen(false)} /> : null}
    </aside>
  )
}
