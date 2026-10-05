import type { ButtonHTMLAttributes } from 'react'
import styles from './Button.module.css'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'text' | 'danger'
  fullWidth?: boolean
  loading?: boolean
}

export function Button({
  variant = 'primary',
  fullWidth,
  loading,
  disabled,
  children,
  className,
  ...rest
}: ButtonProps) {
  return (
    <button
      type="button"
      className={[styles.button, styles[variant], fullWidth ? styles.fullWidth : '', className]
        .filter(Boolean)
        .join(' ')}
      disabled={disabled || loading}
      {...rest}
    >
      {loading ? 'Loading…' : children}
    </button>
  )
}
