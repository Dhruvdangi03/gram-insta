import { useMutation, useQueryClient } from '@tanstack/react-query'
import * as savedApi from '@/lib/api/endpoints/saved'
import { patchPostInAllCaches } from '@/lib/queryHelpers'
import { queryKeys } from '@/lib/queryKeys'

/** Optimistically flips save state everywhere the post is cached; rolls back on failure. Mirrors
 * useLikeMutation's shape exactly — same predicate-based cache patch, same optimistic/rollback flow. */
export function useSaveMutation(postId: number, currentlySaved: boolean) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => (currentlySaved ? savedApi.unsave(postId) : savedApi.save(postId)),
    onMutate: () => {
      const nextSaved = !currentlySaved
      patchPostInAllCaches(queryClient, postId, { savedByViewer: nextSaved })
      return { previousSaved: currentlySaved }
    },
    onError: (_err, _vars, context) => {
      if (context) {
        patchPostInAllCaches(queryClient, postId, { savedByViewer: context.previousSaved })
      }
    },
    onSettled: () => {
      // The dedicated Saved grid is a list of "posts currently saved," not just a cache of post
      // objects — patching `savedByViewer` in place (above) isn't enough to add/remove this post
      // from that specific list, so it needs its own refetch.
      queryClient.invalidateQueries({ queryKey: queryKeys.savedPosts() })
    },
  })
}
