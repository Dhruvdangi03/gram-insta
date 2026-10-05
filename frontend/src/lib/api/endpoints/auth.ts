import { apiFetch } from '../client'
import type { AuthTokens } from '../types'

export interface RegisterRequest {
  username: string
  email: string
  password: string
  fullName?: string
}

export interface LoginRequest {
  usernameOrEmail: string
  password: string
}

export function register(body: RegisterRequest) {
  return apiFetch<AuthTokens>('/auth/register', { method: 'POST', body })
}

export function login(body: LoginRequest) {
  return apiFetch<AuthTokens>('/auth/login', { method: 'POST', body })
}

export function refresh() {
  return apiFetch<AuthTokens>('/auth/refresh', { method: 'POST' })
}

export function logout() {
  return apiFetch<void>('/auth/logout', { method: 'POST' })
}

export function forgotPassword(email: string) {
  return apiFetch<void>('/auth/forgot-password', { method: 'POST', body: { email } })
}

export function resetPassword(token: string, newPassword: string) {
  return apiFetch<void>('/auth/reset-password', { method: 'POST', body: { token, newPassword } })
}
