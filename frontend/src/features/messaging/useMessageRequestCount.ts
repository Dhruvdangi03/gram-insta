import { useQuery } from '@tanstack/react-query'
import * as messagingApi from '@/lib/api/endpoints/messaging'
import { queryKeys } from '@/lib/queryKeys'

export function useMessageRequestCount() {
  const { data } = useQuery({
    queryKey: queryKeys.messageRequestCount(),
    queryFn: messagingApi.getMessageRequestCount,
  })
  return data?.count ?? 0
}
