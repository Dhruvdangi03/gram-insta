import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ActionSheet } from '@/components/ActionSheet'
import { Icon } from '@/components/Icon'
import * as reportsApi from '@/lib/api/endpoints/reports'
import * as usersApi from '@/lib/api/endpoints/users'
import type { UserProfile } from '@/lib/api/types'
import { queryKeys } from '@/lib/queryKeys'

/** The "..." menu on someone else's profile — Block/Restrict/Report, mirroring PostOptionsMenu's
 * ActionSheet pattern. Only rendered for non-self profiles (ProfileHeader owns that check). */
export function ProfileOptionsMenu({ profile, className }: { profile: UserProfile; className?: string }) {
  const queryClient = useQueryClient()
  const [showSheet, setShowSheet] = useState(false)
  const [showReported, setShowReported] = useState(false)

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: queryKeys.userProfile(profile.username) })
  }

  const blockMutation = useMutation({
    mutationFn: () => (profile.viewerHasBlocked ? usersApi.unblockUser(profile.username) : usersApi.blockUser(profile.username)),
    onSuccess: () => {
      invalidate()
      // Already-cached posts from this user can be sitting in the feed/explore lists too, not
      // just this profile — mirrors PostOptionsMenu's block handler so blocking has the same
      // effect regardless of which "..." menu it's triggered from.
      queryClient.invalidateQueries({ queryKey: queryKeys.feed() })
      queryClient.invalidateQueries({ queryKey: queryKeys.explore() })
      queryClient.invalidateQueries({ queryKey: queryKeys.suggestions() })
      queryClient.invalidateQueries({ queryKey: queryKeys.storiesFeed() })
      queryClient.invalidateQueries({ queryKey: queryKeys.userStories(profile.username) })
    },
  })
  const restrictMutation = useMutation({
    mutationFn: () =>
      profile.viewerHasRestricted ? usersApi.unrestrictUser(profile.username) : usersApi.restrictUser(profile.username),
    onSuccess: invalidate,
  })
  const reportMutation = useMutation({
    mutationFn: () => reportsApi.report({ targetType: 'USER', targetId: profile.id }),
    onSuccess: () => setShowReported(true),
  })

  return (
    <>
      <button type="button" className={className} aria-label="More options" onClick={() => setShowSheet(true)}>
        <Icon name="options" />
      </button>
      {showSheet ? (
        <ActionSheet
          onClose={() => setShowSheet(false)}
          actions={[
            { label: 'Report', onClick: () => reportMutation.mutate(), destructive: true },
            {
              label: profile.viewerHasRestricted ? 'Unrestrict' : 'Restrict',
              onClick: () => restrictMutation.mutate(),
            },
            {
              label: profile.viewerHasBlocked ? 'Unblock' : 'Block',
              onClick: () => blockMutation.mutate(),
              destructive: !profile.viewerHasBlocked,
            },
          ]}
        />
      ) : null}
      {showReported ? (
        <ActionSheet onClose={() => setShowReported(false)} actions={[{ label: 'Thanks for reporting this account.', onClick: () => {} }]} />
      ) : null}
    </>
  )
}
