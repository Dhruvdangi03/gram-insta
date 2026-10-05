export type IconName =
  | 'home'
  | 'search'
  | 'explore'
  | 'reels'
  | 'messages'
  | 'heart'
  | 'create'
  | 'profile'
  | 'more'
  | 'comment'
  | 'share'
  | 'bookmark'
  | 'options'
  | 'back'
  | 'close'
  | 'play'
  | 'chevronDown'
  | 'video'
  | 'lock'
  | 'volumeOn'
  | 'volumeOff'
  | 'trash'
  | 'carousel'

interface IconDef {
  /** Rendered with stroke, fill="none" — the default nav/action state. */
  outline: string
  /** Rendered with fill="currentColor" — used for the active nav item / liked / saved state. Falls back to outline when absent. */
  filled?: string
  viewBox?: string
  /** Override the default 1.7 outline stroke width — used by `options`, whose dots need a much thicker round-capped stroke to read as dots rather than hairlines. */
  strokeWidth?: number
}

// Hand-built, in Instagram's visual language (24x24, rounded stroke caps) — not traced from
// Instagram's own asset files. See the Phase 5 plan's trademark note.
export const ICONS: Record<IconName, IconDef> = {
  home: {
    outline: 'M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-7h-4v7H5a1 1 0 0 1-1-1Z',
    filled: 'M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-7h-4v7H5a1 1 0 0 1-1-1Z',
  },
  search: {
    outline: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14Zm10 17-5.6-5.6',
  },
  explore: {
    outline: 'M12 2.5a9.5 9.5 0 1 0 0 19 9.5 9.5 0 0 0 0-19Zm3.6 5.9-2 5.4-5.4 2 2-5.4Z',
    filled: 'M12 2.5a9.5 9.5 0 1 0 0 19 9.5 9.5 0 0 0 0-19Zm3.6 5.9-2 5.4-5.4 2 2-5.4Z',
  },
  reels: {
    outline: 'M3 7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Zm5-2 2.5 4M14 5l2.5 4',
    filled: 'M3 7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Zm5-2 2.5 4M14 5l2.5 4',
  },
  messages: {
    outline: 'M22 3 2.5 10.8 11 13l2.2 8.5Zm0 0L11 13',
    filled: 'M22 3 2.5 10.8 11 13l2.2 8.5Zm0 0L11 13',
  },
  heart: {
    outline:
      'M12.1 20.5S3 15.2 3 9.3A4.8 4.8 0 0 1 12 6.5a4.8 4.8 0 0 1 9 2.8c0 5.9-9.1 11.2-9.1 11.2Z',
    filled:
      'M12.1 20.5S3 15.2 3 9.3A4.8 4.8 0 0 1 12 6.5a4.8 4.8 0 0 1 9 2.8c0 5.9-9.1 11.2-9.1 11.2Z',
  },
  create: {
    outline: 'M3 3h18v18H3ZM12 8v8M8 12h8',
  },
  profile: {
    outline: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-8 9c0-4.4 3.6-7 8-7s8 2.6 8 7',
    filled: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-8 9c0-4.4 3.6-7 8-7s8 2.6 8 7',
  },
  more: {
    outline: 'M4 7h16M4 12h16M4 17h16',
  },
  comment: {
    outline:
      'M21 11.5A8.5 8.5 0 0 1 8.9 19.3L3 21l1.8-5.7A8.5 8.5 0 1 1 21 11.5Z',
  },
  share: {
    outline: 'M22 3 2.5 10.8 11 13l2.2 8.5Zm0 0L11 13',
  },
  bookmark: {
    outline: 'M6 3h12a1 1 0 0 1 1 1v17l-7-4-7 4V4a1 1 0 0 1 1-1Z',
    filled: 'M6 3h12a1 1 0 0 1 1 1v17l-7-4-7 4V4a1 1 0 0 1 1-1Z',
  },
  options: {
    // Zero-length path segments with a round linecap render as dots — avoids needing raw <circle>
    // children just for this one icon.
    outline: 'M5 12h.01M12 12h.01M19 12h.01',
    strokeWidth: 3,
  },
  back: {
    outline: 'M15 18 9 12l6-6',
  },
  close: {
    outline: 'M6 6l12 12M18 6 6 18',
  },
  play: {
    outline: 'M6 4l14 8-14 8Z',
    filled: 'M6 4l14 8-14 8Z',
  },
  chevronDown: {
    outline: 'M6 9l6 6 6-6',
  },
  video: {
    outline: 'M3 6h13v12H3ZM16 10l5-3v10l-5-3Z',
  },
  lock: {
    outline: 'M6 11h12v9H6ZM8 11V8a4 4 0 0 1 8 0v3',
  },
  volumeOn: {
    outline: 'M4 9v6h4l5 4V5L8 9Zm12.5-1a4 4 0 0 1 0 8',
  },
  volumeOff: {
    outline: 'M4 9v6h4l5 4V5L8 9Zm10 1 5 5m0-5-5 5',
  },
  trash: {
    outline: 'M4 7h16M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2m-9 0 1 13a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-13',
  },
  carousel: {
    outline: 'M7 2h13a2 2 0 0 1 2 2v13M4 7h13a2 2 0 0 1 2 2v13H6a2 2 0 0 1-2-2Z',
  },
}
