import { Modal } from '@/components/Modal'
import styles from './ActionSheet.module.css'

export interface ActionSheetAction {
  label: string
  onClick: () => void
  destructive?: boolean
  /** Overrides the React key (defaults to `label`) for when multiple actions can share a label. */
  key?: string | number
}

/** Instagram's centered "..." action-sheet pattern — a list of full-width buttons in a Modal.
 * An optional title/message turns it into a confirmation dialog (e.g. "Delete post?"), so a
 * follow-up confirm step doesn't look like the same menu with an option missing. */
export function ActionSheet({
  onClose,
  actions,
  title,
  message,
}: {
  onClose: () => void
  actions: ActionSheetAction[]
  title?: string
  message?: string
}) {
  return (
    <Modal onClose={onClose} contentClassName={styles.content}>
      {title || message ? (
        <div className={styles.header}>
          {title ? <h2 className={styles.title}>{title}</h2> : null}
          {message ? <p className={styles.message}>{message}</p> : null}
        </div>
      ) : null}
      {actions.map((action) => (
        <button
          key={action.key ?? action.label}
          type="button"
          className={[styles.action, action.destructive ? styles.destructive : ''].join(' ')}
          onClick={() => {
            onClose()
            action.onClick()
          }}
        >
          {action.label}
        </button>
      ))}
      <button type="button" className={styles.cancel} onClick={onClose}>
        Cancel
      </button>
    </Modal>
  )
}
