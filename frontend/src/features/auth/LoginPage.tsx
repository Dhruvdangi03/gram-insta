import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Button } from '@/components/Button'
import { FormField } from '@/components/FormField'
import { useAuth } from '@/contexts/useAuth'
import { ApiError } from '@/lib/api/client'
import { loginSchema, type LoginFormValues } from '@/lib/validators/auth'
import { AuthLayout } from './AuthLayout'
import styles from './AuthLayout.module.css'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginSchema) })

  async function onSubmit(values: LoginFormValues) {
    setFormError(null)
    try {
      await login(values.usernameOrEmail, values.password)
      const from = (location.state as { from?: { pathname: string; search: string } } | null)?.from
      navigate(from ? `${from.pathname}${from.search}` : '/', { replace: true })
    } catch (err) {
      setFormError(err instanceof ApiError ? err.detail : 'Something went wrong. Please try again.')
    }
  }

  return (
    <AuthLayout
      footer={
        <div className={styles.secondaryCard}>
          Don&apos;t have an account? <Link to="/register">Sign up</Link>
        </div>
      }
    >
      <form className={styles.form} onSubmit={handleSubmit(onSubmit)} noValidate>
        {formError ? <p className={styles.formError}>{formError}</p> : null}
        <FormField placeholder="Username or email" autoComplete="username" error={errors.usernameOrEmail?.message} {...register('usernameOrEmail')} />
        <FormField
          type="password"
          placeholder="Password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password')}
        />
        <Button type="submit" fullWidth loading={isSubmitting} className={styles.submit}>
          Log in
        </Button>
      </form>
      <Link to="/forgot-password" className={styles.forgot}>
        Forgot password?
      </Link>
    </AuthLayout>
  )
}
