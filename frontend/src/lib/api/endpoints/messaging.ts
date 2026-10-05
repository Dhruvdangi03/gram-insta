import { apiFetch, buildQuery } from '../client'
import type { Conversation, CursorPage, Message } from '../types'

export interface CreateConversationRequest {
  participantUsernames: string[]
}

export interface SendMessageRequest {
  content?: string
  mediaUrl?: string
}

export function createConversation(body: CreateConversationRequest) {
  return apiFetch<Conversation>('/conversations', { method: 'POST', body })
}

export function getConversations(cursor?: string, limit?: number) {
  return apiFetch<CursorPage<Conversation>>(`/conversations${buildQuery({ cursor, limit })}`)
}

export function sendMessage(conversationId: number, body: SendMessageRequest) {
  return apiFetch<Message>(`/conversations/${conversationId}/messages`, { method: 'POST', body })
}

export function getMessages(conversationId: number, cursor?: string, limit?: number) {
  return apiFetch<CursorPage<Message>>(`/conversations/${conversationId}/messages${buildQuery({ cursor, limit })}`)
}
