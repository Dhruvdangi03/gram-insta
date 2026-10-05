import { useQuery } from '@tanstack/react-query'
import { ActionSheet } from '@/components/ActionSheet'
import * as storiesApi from '@/lib/api/endpoints/stories'
import { formatRelativeTime } from '@/lib/formatters/relativeTime'
import { queryKeys } from '@/lib/queryKeys'

/** ProfilePage's "+ New" highlight circle, step 1: pick one of the user's currently active
 * stories to start the highlight from. Reports the pick via `onStorySelected` rather than
 * rendering CreateHighlightModal itself — ActionSheet always calls `onClose` before an action's
 * onClick, so if this component owned the next step's state, the parent-controlled unmount
 * (triggered by that same onClose) would discard it in the same render before it ever showed;
 * lifting the "which step" state to the parent (which isn't unmounting) avoids that. */
export function NewHighlightFlow({
  username,
  onClose,
  onStorySelected,
}: {
  username: string
  onClose: () => void
  onStorySelected: (storyId: number) => void
}) {
  const { data: stories } = useQuery({
    queryKey: queryKeys.userStories(username),
    queryFn: () => storiesApi.getUserStories(username),
  })

  if (!stories) {
    return null
  }

  if (stories.length === 0) {
    return (
      <ActionSheet onClose={onClose} actions={[{ label: 'Share a story first to create a highlight.', onClick: () => {} }]} />
    )
  }

  return (
    <ActionSheet
      onClose={onClose}
      actions={stories.map((s) => ({
        key: s.id,
        label: `Story from ${formatRelativeTime(s.createdAt)}`,
        onClick: () => onStorySelected(s.id),
      }))}
    />
  )
}
