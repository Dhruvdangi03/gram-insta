import { Fragment, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

// Mirrors the backend's TextEntities (common/util/TextEntities.java) — keep the two in step so what
// renders as a link is exactly what the server treats as a mention/hashtag (and notifies for).
// Mentions: 3-30 of letters/digits/'.'/'_', not glued to a word char, '@' or '.' (so no emails).
// Hashtags: 1-50 letters/digits/'_', not glued to a letter/digit/'_'/'&'/'#', with a letter somewhere.
const ENTITY = /(?<![\w@.])@([A-Za-z0-9._]{3,30})|(?<![\p{L}\p{N}_&#])#([\p{L}\p{N}_]{1,50})/gu

export interface RichTextProps {
  text: string
  /** Applied to both @mention and #hashtag links. */
  linkClassName?: string
}

/** Renders text with @mentions linked to profiles and #hashtags linked to their tag page. */
export function RichText({ text, linkClassName }: RichTextProps) {
  const nodes: ReactNode[] = []
  let cursor = 0

  for (const match of text.matchAll(ENTITY)) {
    const start = match.index ?? 0
    let node: ReactNode = null
    let consumed = match[0].length

    if (match[1] !== undefined) {
      // "thanks @bob." — a trailing '.' is sentence punctuation, not part of the username.
      const username = match[1].replace(/\.+$/, '')
      if (username.length >= 3) {
        consumed = 1 + username.length
        node = (
          <Link to={`/${username}`} className={linkClassName}>
            @{username}
          </Link>
        )
      }
    } else if (match[2] !== undefined && /\p{L}/u.test(match[2])) {
      node = (
        <Link to={`/explore/tags/${encodeURIComponent(match[2].toLowerCase())}`} className={linkClassName}>
          #{match[2]}
        </Link>
      )
    }

    if (node) {
      if (start > cursor) nodes.push(<Fragment key={`t${cursor}`}>{text.slice(cursor, start)}</Fragment>)
      nodes.push(<Fragment key={`e${start}`}>{node}</Fragment>)
      cursor = start + consumed
    }
  }
  if (cursor < text.length) nodes.push(<Fragment key={`t${cursor}`}>{text.slice(cursor)}</Fragment>)

  return <>{nodes}</>
}
