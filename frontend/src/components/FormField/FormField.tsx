import { forwardRef, type InputHTMLAttributes } from 'react'
import styles from './FormField.module.css'

export interface FormFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  error?: string
}

export const FormField = forwardRef<HTMLInputElement, FormFieldProps>(function FormField(
  { error, className, ...rest },
  ref,
) {
  return (
    <div className={styles.field}>
      <input
        ref={ref}
        className={[styles.input, error ? styles.invalid : '', className].filter(Boolean).join(' ')}
        aria-invalid={Boolean(error)}
        {...rest}
      />
      {error ? <span className={styles.error}>{error}</span> : null}
    </div>
  )
})
