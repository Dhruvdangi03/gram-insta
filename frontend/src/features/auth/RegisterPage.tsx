import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '@/components/Button'
import { FormField } from '@/components/FormField'
import { useAuth } from '@/contexts/useAuth'
import { ApiError } from '@/lib/api/client'
import { registerSchema, type RegisterFormValues } from '@/lib/validators/auth'
import { AuthLayout } from './AuthLayout'
import styles from './AuthLayout.module.css'

export function RegisterPage() {
  const { register: registerUser } = useAuth()
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({ resolver: zodResolver(registerSchema) })

  async function onSubmit(values: RegisterFormValues) {
    setFormError(null)
    try {
      await registerUser(values.username, values.email, values.password, values.fullName || undefined)
      navigate('/', { replace: true })
    } catch (err) {
      setFormError(err instanceof ApiError ? err.detail : 'Something went wrong. Please try again.')
    }
  }

  return (
    <AuthLayout
      footer={
        <div className={styles.secondaryCard}>
          Have an account? <Link to="/login">Log in</Link>
        </div>
      }
    >
      <p className={styles.helperText}>Sign up to see photos and videos from your friends.</p>
      <form className={styles.form} onSubmit={handleSubmit(onSubmit)} noValidate>
        {formError ? <p className={styles.formError}>{formError}</p> : null}
        <FormField type="email" placeholder="Email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <FormField placeholder="Full Name" autoComplete="name" error={errors.fullName?.message} {...register('fullName')} />
        <FormField placeholder="Username" autoComplete="username" error={errors.username?.message} {...register('username')} />
        <FormField
          type="password"
          placeholder="Password"
          autoComplete="new-password"
          error={errors.password?.message}
          {...register('password')}
        />
        <p className={styles.helperText}>
          By signing up, you agree to our Terms, Data Policy and Cookies Policy.
        </p>
        <Button type="submit" fullWidth loading={isSubmitting} className={styles.submit}>
          Sign up
        </Button>
      </form>
    </AuthLayout>
  )
}
