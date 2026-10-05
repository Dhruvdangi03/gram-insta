import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { Button } from '@/components/Button'
import { FormField } from '@/components/FormField'
import * as authApi from '@/lib/api/endpoints/auth'
import { forgotPasswordSchema, type ForgotPasswordFormValues } from '@/lib/validators/auth'
import { AuthLayout } from './AuthLayout'
import styles from './AuthLayout.module.css'

export function ForgotPasswordPage() {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ForgotPasswordFormValues>({ resolver: zodResolver(forgotPasswordSchema) })

  const mutation = useMutation({
    mutationFn: (values: ForgotPasswordFormValues) => authApi.forgotPassword(values.email),
  })

  return (
    <AuthLayout
      footer={
        <div className={styles.secondaryCard}>
          <Link to="/login">Back to log in</Link>
        </div>
      }
    >
      {mutation.isSuccess ? (
        <p className={styles.helperText}>
          If an account exists for that email, we&apos;ve sent a link to reset your password.
        </p>
      ) : (
        <>
          <p className={styles.helperText}>Enter your email and we&apos;ll send you a link to reset your password.</p>
          <form className={styles.form} onSubmit={handleSubmit((values) => mutation.mutate(values))} noValidate>
            <FormField type="email" placeholder="Email" autoComplete="email" error={errors.email?.message} {...register('email')} />
            <Button type="submit" fullWidth loading={mutation.isPending} className={styles.submit}>
              Send reset link
            </Button>
          </form>
        </>
      )}
    </AuthLayout>
  )
}
