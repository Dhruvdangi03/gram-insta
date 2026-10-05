import type { ReactNode } from 'react'
import { Outlet } from 'react-router-dom'
import { MobileTabBar, MobileTopBar } from './MobileTabBar'
import { Sidebar } from './Sidebar'
import styles from './AppShell.module.css'

export function AppShell({ children }: { children?: ReactNode }) {
  return (
    <div className={styles.shell}>
      <Sidebar />
      <MobileTopBar />
      <main className={styles.main}>{children ?? <Outlet />}</main>
      <MobileTabBar />
    </div>
  )
}
