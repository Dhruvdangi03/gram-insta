import { Fragment } from 'react'
import { Link } from 'react-router-dom'

export interface CaptionTextProps {
  username: string
  caption: string
  usernameClassName?: string
  hashtagClassName?: string
  className?: string
  /** Reels show the author elsewhere in their own overlay row, so the caption there shouldn't repeat a linked username. */
  showUsername?: boolean
}

/** Renders "username caption text" with the username linked to the profile and #tags highlighted (plain text, not links) — shared by PostCard, PostDetail, and ReelItem so caption parsing lives in one place. */
export function CaptionText({
  username,
  caption,
  usernameClassName,
  hashtagClassName,
  className,
  showUsername = true,
}: CaptionTextProps) {
  const parts = caption.split(/(#\w+)/g)
  return (
    <p className={className}>
      {showUsername ? (
        <Link to={`/${username}`} className={usernameClassName}>
          {username}
        </Link>
      ) : null}
      {parts.map((part, i) =>
        part.startsWith('#') ? (
          <span key={i} className={hashtagClassName}>
            {part}
          </span>
        ) : (
          <Fragment key={i}>{part}</Fragment>
        ),
      )}
    </p>
  )
}
