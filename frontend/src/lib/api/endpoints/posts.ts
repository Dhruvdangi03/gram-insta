import { apiFetch, buildQuery } from '../client'
import type { CursorPage, PresignedUpload, Post } from '../types'

export interface CreateUploadUrlRequest {
  contentType: string
}

export interface CreatePostRequest {
  caption?: string
  location?: string
  media: { url: string; width?: number; height?: number }[]
}

export interface UpdatePostRequest {
  caption?: string
  location?: string
}

export function createUploadUrl(body: CreateUploadUrlRequest) {
  return apiFetch<PresignedUpload>('/posts/upload-url', { method: 'POST', body })
}

export function createPost(body: CreatePostRequest) {
  return apiFetch<Post>('/posts', { method: 'POST', body })
}

export function getPost(id: number) {
  return apiFetch<Post>(`/posts/${id}`)
}

export function updatePost(id: number, body: UpdatePostRequest) {
  return apiFetch<Post>(`/posts/${id}`, { method: 'PATCH', body })
}

export function deletePost(id: number) {
  return apiFetch<void>(`/posts/${id}`, { method: 'DELETE' })
}

export function getUserPosts(username: string, cursor?: string, limit?: number) {
  return apiFetch<CursorPage<Post>>(`/users/${encodeURIComponent(username)}/posts${buildQuery({ cursor, limit })}`)
}

/** Uploads the raw file straight to the presigned URL — not through apiFetch (no auth header, no JSON body). */
export async function uploadToPresignedUrl(uploadUrl: string, file: File) {
  const response = await fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': file.type },
    body: file,
  })
  if (!response.ok) {
    throw new Error(`Upload failed: ${response.status}`)
  }
}
