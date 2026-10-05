import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { Avatar } from '@/components/Avatar'
import { Button } from '@/components/Button'
import { useAuth } from '@/contexts/useAuth'
import { ApiError } from '@/lib/api/client'
import * as postsApi from '@/lib/api/endpoints/posts'
import * as usersApi from '@/lib/api/endpoints/users'
import type { UserProfile } from '@/lib/api/types'
import { queryKeys } from '@/lib/queryKeys'
import styles from './EditProfilePage.module.css'

const AVATAR_TYPE_PATTERN = /^image\/(jpeg|png|webp)$/

export function EditProfilePage() {
  const { user } = useAuth()

  // AuthContext's `user` is only a UserSummary (id/username/fullName/profilePictureUrl) — it has
  // no bio/isPrivate. The form below needs the full profile to initialize from, so it only mounts
  // once this has loaded (its useState initial values then come straight from real props, no
  // effect-based sync needed to avoid starting bio blank / isPrivate false and silently wiping
  // real saved values on Submit).
  const { data: profile } = useQuery({
    queryKey: queryKeys.userProfile(user?.username ?? ''),
    queryFn: () => usersApi.getProfile(user!.username),
    enabled: Boolean(user),
  })

  if (!user || !profile) return null

  return <EditProfileForm profile={profile} />
}

function EditProfileForm({ profile }: { profile: UserProfile }) {
  const { updateUser } = useAuth()
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  const [fullName, setFullName] = useState(profile.fullName ?? '')
  const [bio, setBio] = useState(profile.bio ?? '')
  const [isPrivate, setIsPrivate] = useState(profile.isPrivate)
  const [isBusiness, setIsBusiness] = useState(profile.isBusiness)
  const [avatarUrl, setAvatarUrl] = useState(profile.profilePictureUrl)
  const [saved, setSaved] = useState(false)

  const avatarMutation = useMutation({
    mutationFn: async (file: File) => {
      const upload = await postsApi.createUploadUrl({ contentType: file.type })
      await postsApi.uploadToPresignedUrl(upload.uploadUrl, file)
      return usersApi.updateMyProfile({ profilePictureUrl: upload.publicUrl })
    },
    onSuccess: (updated) => {
      setAvatarUrl(updated.profilePictureUrl)
      updateUser({ profilePictureUrl: updated.profilePictureUrl })
      queryClient.invalidateQueries({ queryKey: queryKeys.userProfile(profile.username) })
    },
  })

  const saveMutation = useMutation({
    mutationFn: () => usersApi.updateMyProfile({ fullName, bio, isPrivate, isBusiness }),
    onSuccess: (updated) => {
      updateUser({ fullName: updated.fullName })
      queryClient.invalidateQueries({ queryKey: queryKeys.userProfile(profile.username) })
      setSaved(true)
      window.setTimeout(() => setSaved(false), 2000)
    },
  })

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Edit profile</h1>

      <div className={styles.avatarRow}>
        <Avatar src={avatarUrl} alt={profile.username} size={56} />
        <div className={styles.avatarMeta}>
          <span className={styles.username}>{profile.username}</span>
          <button type="button" className={styles.changePhotoButton} onClick={() => fileInputRef.current?.click()}>
            Change profile photo
          </button>
        </div>
        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          hidden
          onChange={(e) => {
            const file = e.target.files?.[0]
            if (file && AVATAR_TYPE_PATTERN.test(file.type)) {
              avatarMutation.mutate(file)
            }
          }}
        />
      </div>

      <div className={styles.field}>
        <label className={styles.fieldLabel} htmlFor="fullName">
          Name
        </label>
        <div className={styles.fieldInputWrapper}>
          <input
            id="fullName"
            className={styles.textarea}
            style={{ minHeight: 'auto' }}
            value={fullName}
            maxLength={100}
            onChange={(e) => setFullName(e.target.value)}
          />
        </div>
      </div>

      <div className={styles.field}>
        <label className={styles.fieldLabel} htmlFor="bio">
          Bio
        </label>
        <div className={styles.fieldInputWrapper}>
          <textarea id="bio" className={styles.textarea} value={bio} maxLength={150} onChange={(e) => setBio(e.target.value)} />
        </div>
      </div>

      <div className={styles.field}>
        <span className={styles.fieldLabel}>Private account</span>
        <div className={styles.fieldInputWrapper}>
          <div className={styles.toggleRow}>
            <p className={styles.toggleDescription}>When your account is private, only people you approve can see your photos and videos.</p>
            <button
              type="button"
              className={[styles.switch, isPrivate ? styles.switchOn : ''].join(' ')}
              role="switch"
              aria-checked={isPrivate}
              onClick={() => setIsPrivate((v) => !v)}
            >
              <span className={[styles.switchKnob, isPrivate ? styles.switchKnobOn : ''].join(' ')} />
            </button>
          </div>
        </div>
      </div>

      <div className={styles.field}>
        <span className={styles.fieldLabel}>Business account</span>
        <div className={styles.fieldInputWrapper}>
          <div className={styles.toggleRow}>
            <p className={styles.toggleDescription}>Business accounts get access to Insights — aggregate stats about your posts and audience.</p>
            <button
              type="button"
              className={[styles.switch, isBusiness ? styles.switchOn : ''].join(' ')}
              role="switch"
              aria-checked={isBusiness}
              onClick={() => setIsBusiness((v) => !v)}
            >
              <span className={[styles.switchKnob, isBusiness ? styles.switchKnobOn : ''].join(' ')} />
            </button>
          </div>
        </div>
      </div>

      <div className={styles.field}>
        <span className={styles.fieldLabel} />
        <div className={styles.fieldInputWrapper}>
          <Button loading={saveMutation.isPending} onClick={() => saveMutation.mutate()}>
            Submit
          </Button>
          {saved ? <p className={styles.successText}>Profile saved.</p> : null}
          {saveMutation.isError ? (
            <p className={styles.successText} style={{ color: 'var(--color-danger)' }}>
              {saveMutation.error instanceof ApiError ? saveMutation.error.detail : 'Something went wrong.'}
            </p>
          ) : null}
        </div>
      </div>
    </div>
  )
}
