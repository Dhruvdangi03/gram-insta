import { useMutation, useQueryClient } from '@tanstack/react-query'
import * as restrictionsApi from '@/lib/api/endpoints/restrictions'
import { queryKeys } from '@/lib/queryKeys'

/**
 * Blocking and muting change what every feed-like surface returns (the server drops the account
 * from home, explore, reels, stories, search and suggestions) and blocking also severs follows, so
 * unlike follow/unfollow this refetches those caches instead of patching one profile entry.
 */
export function useBlockMuteMutation(username: string) {
  const queryClient = useQueryClient()

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.userProfile(username) })
    queryClient.invalidateQueries({ queryKey: queryKeys.feed() })
    queryClient.invalidateQueries({ queryKey: queryKeys.explore() })
    queryClient.invalidateQueries({ queryKey: queryKeys.reelsFeed() })
    queryClient.invalidateQueries({ queryKey: queryKeys.storiesFeed() })
    queryClient.invalidateQueries({ queryKey: queryKeys.suggestions() })
    queryClient.invalidateQueries({ queryKey: queryKeys.blockedUsers() })
    queryClient.invalidateQueries({ queryKey: queryKeys.mutedUsers() })
    queryClient.invalidateQueries({ queryKey: queryKeys.conversations() })
    queryClient.invalidateQueries({ queryKey: queryKeys.messageRequests() })
  }

  return {
    block: useMutation({ mutationFn: () => restrictionsApi.blockUser(username), onSuccess: refresh }),
    unblock: useMutation({ mutationFn: () => restrictionsApi.unblockUser(username), onSuccess: refresh }),
    mute: useMutation({ mutationFn: () => restrictionsApi.muteUser(username), onSuccess: refresh }),
    unmute: useMutation({ mutationFn: () => restrictionsApi.unmuteUser(username), onSuccess: refresh }),
  }
}
