import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ActionSheet } from '@/components/ActionSheet'
import * as highlightsApi from '@/lib/api/endpoints/highlights'
import { queryKeys } from '@/lib/queryKeys'

/** StoryViewer's "Add to highlight" action on an own active story — pick an existing highlight to
 * append to, or start a new one. Reports "start a new one" via `onCreateNew` rather than owning
 * that step itself, for the same reason NewHighlightFlow doesn't own CreateHighlightModal directly
 * — see its comment. */
export function AddToHighlightSheet({
  storyId,
  username,
  onClose,
  onCreateNew,
}: {
  storyId: number
  username: string
  onClose: () => void
  onCreateNew: () => void
}) {
  const queryClient = useQueryClient()
  const { data: highlights } = useQuery({
    queryKey: queryKeys.highlights(username),
    queryFn: () => highlightsApi.getHighlights(username),
  })

  const addMutation = useMutation({
    mutationFn: (highlightId: number) => highlightsApi.addHighlightItem(highlightId, storyId),
    onSuccess: (_result, highlightId) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.highlightDetail(highlightId) })
    },
    // ActionSheet closes this sheet as soon as an action is tapped, before this request even
    // settles — without this, a failure would be completely invisible to the user.
    onError: () => window.alert('Something went wrong adding this story to the highlight. Please try again.'),
  })

  return (
    <ActionSheet
      onClose={onClose}
      actions={[
        ...(highlights ?? []).map((h) => ({ label: h.title, onClick: () => addMutation.mutate(h.id) })),
        { label: 'New highlight', onClick: onCreateNew },
      ]}
    />
  )
}
