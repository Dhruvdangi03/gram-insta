import type { ReactNode } from 'react'
import { AppIcon } from '@/components/AppIcon'
import { Wordmark } from '@/components/Wordmark'
import styles from './AuthLayout.module.css'

export function AuthLayout({ children, footer }: { children: ReactNode; footer?: ReactNode }) {
  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <div className={styles.logoLockup}>
          <AppIcon size={64} />
          <Wordmark className={styles.logo} />
        </div>
        {children}
      </div>
      {footer}
    </div>
  )
}
