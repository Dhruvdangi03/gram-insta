import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/Button'
import { FormField } from '@/components/FormField'
import { ApiError } from '@/lib/api/client'
import * as authApi from '@/lib/api/endpoints/auth'
import { resetPasswordSchema, type ResetPasswordFormValues } from '@/lib/validators/auth'
import { AuthLayout } from './AuthLayout'
import styles from './AuthLayout.module.css'

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const navigate = useNavigate()

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ResetPasswordFormValues>({ resolver: zodResolver(resetPasswordSchema) })

  const mutation = useMutation({
    mutationFn: (values: ResetPasswordFormValues) => authApi.resetPassword(token!, values.newPassword),
    onSuccess: () => navigate('/login', { replace: true }),
  })

  if (!token) {
    return (
      <AuthLayout>
        <p className={styles.helperText}>
          This reset link is missing its token. <Link to="/forgot-password">Request a new one</Link>.
        </p>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout>
      <p className={styles.helperText}>Enter a new password for your account.</p>
      <form className={styles.form} onSubmit={handleSubmit((values) => mutation.mutate(values))} noValidate>
        {mutation.isError ? (
          <p className={styles.formError}>
            {mutation.error instanceof ApiError ? mutation.error.detail : 'Something went wrong. Please try again.'}
          </p>
        ) : null}
        <FormField
          type="password"
          placeholder="New password"
          autoComplete="new-password"
          error={errors.newPassword?.message}
          {...register('newPassword')}
        />
        <Button type="submit" fullWidth loading={mutation.isPending} className={styles.submit}>
          Reset password
        </Button>
      </form>
    </AuthLayout>
  )
}
