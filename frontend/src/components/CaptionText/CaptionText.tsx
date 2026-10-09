import { Link } from 'react-router-dom'
import { RichText } from '@/components/RichText'

export interface CaptionTextProps {
  username: string
  caption: string
  usernameClassName?: string
  hashtagClassName?: string
  className?: string
  /** Reels show the author elsewhere in their own overlay row, so the caption there shouldn't repeat a linked username. */
  showUsername?: boolean
}

/** Renders "username caption text" with the username linked to the profile and @mentions / #hashtags linked via RichText — shared by PostCard, PostDetail, and ReelItem so caption parsing lives in one place. */
export function CaptionText({
  username,
  caption,
  usernameClassName,
  hashtagClassName,
  className,
  showUsername = true,
}: CaptionTextProps) {
  return (
    <p className={className}>
      {showUsername ? (
        <Link to={`/${username}`} className={usernameClassName}>
          {username}
        </Link>
      ) : null}
      <RichText text={caption} linkClassName={hashtagClassName} />
    </p>
  )
}
