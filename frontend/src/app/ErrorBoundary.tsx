import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Button } from '@/components/Button'
import { Wordmark } from '@/components/Wordmark'
import styles from './ErrorBoundary.module.css'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
}

/**
 * React error boundaries have no hook equivalent — a class component is required. Catches any
 * uncaught render-time error anywhere in the tree and shows a recoverable page instead of a blank
 * white screen, matching how apiFetch/ApiError already handles request-time failures gracefully.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false }

  static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Uncaught render error', error, info)
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className={styles.page}>
          <Wordmark />
          <p className={styles.title}>Something went wrong.</p>
          <p className={styles.subtitle}>An unexpected error occurred. Try reloading the page.</p>
          <Button onClick={() => window.location.reload()}>Reload</Button>
        </div>
      )
    }
    return this.props.children
  }
}
