import { apiFetch, buildQuery } from '../client'
import type { Conversation, CursorPage, Message } from '../types'

export interface CreateConversationRequest {
  participantUsernames: string[]
}

export interface SendMessageRequest {
  content?: string
  mediaUrl?: string
  sharedPostId?: number
}

export function createConversation(body: CreateConversationRequest) {
  return apiFetch<Conversation>('/conversations', { method: 'POST', body })
}

export function getConversations(cursor?: string, limit?: number) {
  return apiFetch<CursorPage<Conversation>>(`/conversations${buildQuery({ cursor, limit })}`)
}

export function getMessageRequests(cursor?: string, limit?: number) {
  return apiFetch<CursorPage<Conversation>>(`/conversations/requests${buildQuery({ cursor, limit })}`)
}

export function getMessageRequestCount() {
  return apiFetch<{ count: number }>('/conversations/requests/count')
}

export function acceptMessageRequest(conversationId: number) {
  return apiFetch<Conversation>(`/conversations/${conversationId}/accept`, { method: 'POST' })
}

export function declineMessageRequest(conversationId: number) {
  return apiFetch<void>(`/conversations/${conversationId}/request`, { method: 'DELETE' })
}

export function sendMessage(conversationId: number, body: SendMessageRequest) {
  return apiFetch<Message>(`/conversations/${conversationId}/messages`, { method: 'POST', body })
}

export function getMessages(conversationId: number, cursor?: string, limit?: number) {
  return apiFetch<CursorPage<Message>>(`/conversations/${conversationId}/messages${buildQuery({ cursor, limit })}`)
}
