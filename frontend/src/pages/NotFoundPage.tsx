import { Link } from 'react-router-dom'
import { Wordmark } from '@/components/Wordmark'
import styles from './NotFoundPage.module.css'

export function NotFoundPage() {
  return (
    <div className={styles.page}>
      <Wordmark />
      <p className={styles.title} style={{ marginTop: 'var(--space-6)' }}>
        Sorry, this page isn&apos;t available.
      </p>
      <p className={styles.subtitle}>
        The link you followed may be broken, or the page may have been removed.
      </p>
      <Link to="/" className={styles.link}>
        Go back to Instaclone
      </Link>
    </div>
  )
}
