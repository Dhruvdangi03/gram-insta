import { useAuth } from '@/contexts/useAuth'
import type { Conversation } from '@/lib/api/types'

/** The conversation's participants minus the viewer — what should actually be displayed as "who this is with". */
export function useOtherParticipants(conversation: Conversation) {
  const { user } = useAuth()
  return conversation.participants.filter((p) => p.id !== user?.id)
}
