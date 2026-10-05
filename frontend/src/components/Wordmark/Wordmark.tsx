import styles from './Wordmark.module.css'

/**
 * Set in Yellowtail, an open-licensed script font matching the casual brush-script register of
 * Instagram's original 2010 "Billabong" logotype — not Billabong itself, which needs a paid
 * commercial license from its foundry. Original brand name, borrowed typeface style only.
 */
export function Wordmark({ className }: { className?: string }) {
  return (
    <span className={[styles.wordmark, className].filter(Boolean).join(' ')}>Instaclone</span>
  )
}
