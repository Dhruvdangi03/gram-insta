import { useMutation, useQueryClient } from '@tanstack/react-query'
import * as likesApi from '@/lib/api/endpoints/likes'
import { patchPostInAllCaches } from '@/lib/queryHelpers'

/** Optimistically flips like state everywhere the post is cached; rolls back on failure. */
export function useLikeMutation(postId: number, currentlyLiked: boolean, currentCount: number) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => (currentlyLiked ? likesApi.unlike(postId) : likesApi.like(postId)),
    onMutate: () => {
      const nextLiked = !currentlyLiked
      const nextCount = currentCount + (nextLiked ? 1 : -1)
      patchPostInAllCaches(queryClient, postId, { likedByViewer: nextLiked, likeCount: nextCount })
      return { previousLiked: currentlyLiked, previousCount: currentCount }
    },
    onError: (_err, _vars, context) => {
      if (context) {
        patchPostInAllCaches(queryClient, postId, {
          likedByViewer: context.previousLiked,
          likeCount: context.previousCount,
        })
      }
    },
    onSuccess: (result) => {
      // Trust the server's authoritative count over the optimistic guess, without a full refetch.
      patchPostInAllCaches(queryClient, postId, { likedByViewer: result.likedByViewer, likeCount: result.likeCount })
    },
  })
}
