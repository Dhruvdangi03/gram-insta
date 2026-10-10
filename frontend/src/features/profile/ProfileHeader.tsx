import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Button } from '@/components/Button'
import { Icon } from '@/components/Icon'
import { ReportSheet } from '@/components/ReportSheet'
import { VerifiedBadge } from '@/components/VerifiedBadge'
import * as messagingApi from '@/lib/api/endpoints/messaging'
import * as usersApi from '@/lib/api/endpoints/users'
import type { UserProfile } from '@/lib/api/types'
import { useBlockMuteMutation } from '@/lib/hooks/useBlockMuteMutation'
import { useFollowMutation } from '@/lib/hooks/useFollowMutation'
import { queryKeys } from '@/lib/queryKeys'
import styles from './ProfileHeader.module.css'

export function ProfileHeader({ profile }: { profile: UserProfile }) {
  const location = useLocation()
  const navigate = useNavigate()
  // Opens (or creates) the 1:1 chat. For someone who doesn't follow you back it starts as a
  // message request on their side; the sender lands in the thread either way.
  const startChat = useMutation({
    mutationFn: () => messagingApi.createConversation({ participantUsernames: [profile.username] }),
    onSuccess: (conversation) => navigate(`/direct/inbox/${conversation.id}`),
  })
  const [showReport, setShowReport] = useState(false)
  const { follow, unfollow } = useFollowMutation(profile.username, profile)
  const { block, unblock, mute, unmute } = useBlockMuteMutation(profile.username)
  const isSelfPrivate = profile.viewerRelationship === 'SELF' && profile.isPrivate
  // Shares queryKeys.followRequests() with FollowRequestsPage, so accepting/declining a request
  // there keeps this count in sync without a separate invalidation.
  const { data: followRequests } = useQuery({
    queryKey: queryKeys.followRequests(),
    queryFn: () => usersApi.getFollowRequests(),
    enabled: isSelfPrivate,
  })

  return (
    <header className={styles.header}>
      <div className={styles.avatarColumn}>
        <Avatar src={profile.profilePictureUrl} alt={profile.username} size={150} className={styles.avatar} />
      </div>
      <div className={styles.info}>
        <div className={styles.topRow}>
          <h1 className={styles.username}>
            {profile.username}
            {profile.isVerified ? <VerifiedBadge size={16} className={styles.verifiedBadge} /> : null}
          </h1>
          <div className={styles.actions}>
            {profile.viewerRelationship === 'SELF' ? (
              <>
                <Link to="/accounts/edit">
                  <Button variant="secondary">Edit profile</Button>
                </Link>
                {profile.isBusiness ? (
                  <Link to="/accounts/insights">
                    <Button variant="secondary">Insights</Button>
                  </Link>
                ) : null}
                {profile.isPrivate ? (
                  <Link to="/accounts/follow-requests">
                    <Button variant="secondary">
                      Follow Requests{followRequests && followRequests.length > 0 ? ` (${followRequests.length})` : ''}
                    </Button>
                  </Link>
                ) : null}
                <Link to="/accounts/blocked-muted">
                  <Button variant="secondary">Blocked &amp; muted</Button>
                </Link>
                <Link to="/accounts/edit" className={styles.settingsButton} aria-label="Settings">
                  <Icon name="more" />
                </Link>
              </>
            ) : (
              <>
                {profile.blockedByViewer ? (
                  <Button variant="secondary" onClick={() => unblock.mutate()} loading={unblock.isPending}>
                    Unblock
                  </Button>
                ) : (
                  <>
                {profile.viewerRelationship === 'FOLLOWING' ? (
                  <>
                    <Button variant="secondary" onClick={() => unfollow.mutate()} loading={unfollow.isPending}>
                      Following
                    </Button>
                    <Button variant="secondary" onClick={() => startChat.mutate()} loading={startChat.isPending}>
                      Message
                    </Button>
                  </>
                ) : profile.viewerRelationship === 'REQUESTED' ? (
                  <>
                    <Button variant="secondary" onClick={() => unfollow.mutate()} loading={unfollow.isPending}>
                      Requested
                    </Button>
                    <Button variant="secondary" onClick={() => startChat.mutate()} loading={startChat.isPending}>
                      Message
                    </Button>
                  </>
                ) : (
                  <>
                    <Button onClick={() => follow.mutate()} loading={follow.isPending}>
                      Follow
                    </Button>
                    <Button variant="secondary" onClick={() => startChat.mutate()} loading={startChat.isPending}>
                      Message
                    </Button>
                  </>
                )}
                    <Button
                      variant="secondary"
                      onClick={() => (profile.mutedByViewer ? unmute.mutate() : mute.mutate())}
                      loading={mute.isPending || unmute.isPending}
                    >
                      {profile.mutedByViewer ? 'Unmute' : 'Mute'}
                    </Button>
                    <Button
                      variant="secondary"
                      onClick={() => {
                        if (window.confirm(`Block ${profile.username}? They won't be able to find your profile or posts.`)) {
                          block.mutate()
                        }
                      }}
                      loading={block.isPending}
                    >
                      Block
                    </Button>
                    <Button variant="secondary" onClick={() => setShowReport(true)}>
                      Report
                    </Button>
                  </>
                )}
              </>
            )}
          </div>
        </div>

        <ul className={styles.stats}>
          <li>
            <span className={styles.statCount}>{profile.postCount}</span>posts
          </li>
          <li>
            <Link to={`/${profile.username}/followers`} state={{ backgroundLocation: location }}>
              <span className={styles.statCount}>{profile.followerCount}</span>followers
            </Link>
          </li>
          <li>
            <Link to={`/${profile.username}/following`} state={{ backgroundLocation: location }}>
              <span className={styles.statCount}>{profile.followingCount}</span>following
            </Link>
          </li>
        </ul>

        {profile.fullName ? <p className={styles.fullName}>{profile.fullName}</p> : null}
        {profile.bio ? <p className={styles.bio}>{profile.bio}</p> : null}
      </div>
      {showReport ? <ReportSheet target={{ type: 'USER', username: profile.username }} onClose={() => setShowReport(false)} /> : null}
    </header>
  )
}
