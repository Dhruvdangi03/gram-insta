import { z } from 'zod'

// Mirrors the backend's @Valid constraints on RegisterRequest/LoginRequest exactly (auth/RegisterRequest.java,
// auth/LoginRequest.java) so client-side errors match what the server would say.
export const registerSchema = z.object({
  username: z
    .string()
    .trim()
    .min(3, 'Username must be 3-30 characters')
    .max(30, 'Username must be 3-30 characters')
    .regex(/^[a-zA-Z0-9._]+$/, 'Username can only contain letters, numbers, periods, and underscores'),
  email: z.string().trim().min(1, 'Email is required').email('Enter a valid email address'),
  password: z.string().min(8, 'Password must be at least 8 characters').max(72, 'Password is too long'),
  fullName: z.string().trim().max(100, 'Full name is too long').optional().or(z.literal('')),
})
export type RegisterFormValues = z.infer<typeof registerSchema>

export const loginSchema = z.object({
  usernameOrEmail: z.string().trim().min(1, 'Enter your username or email'),
  password: z.string().min(1, 'Enter your password'),
})
export type LoginFormValues = z.infer<typeof loginSchema>

export const forgotPasswordSchema = z.object({
  email: z.string().trim().min(1, 'Email is required').email('Enter a valid email address'),
})
export type ForgotPasswordFormValues = z.infer<typeof forgotPasswordSchema>

export const resetPasswordSchema = z.object({
  newPassword: z.string().min(8, 'Password must be at least 8 characters').max(72, 'Password is too long'),
})
export type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>
