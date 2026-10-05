// Hand-mirrored from the backend's actual DTOs (com.instaclone.*), verified against source during
// Phase 5 planning. Keep in sync by hand — there is no codegen step, see the Phase 5 plan doc.

export interface CursorPage<T> {
  items: T[]
  nextCursor: string | null
  hasMore: boolean
}

export interface ProblemDetail {
  type: string
  title: string
  status: number
  detail: string
  instance?: string
  errors?: Record<string, string>
}

export interface UserSummary {
  id: number
  username: string
  fullName: string | null
  profilePictureUrl: string | null
  isVerified: boolean
}

export type ViewerRelationship = 'SELF' | 'FOLLOWING' | 'REQUESTED' | 'NOT_FOLLOWING'

export interface UserProfile {
  id: number
  username: string
  fullName: string | null
  bio: string | null
  profilePictureUrl: string | null
  isPrivate: boolean
  isVerified: boolean
  isBusiness: boolean
  postCount: number
  followerCount: number
  followingCount: number
  viewerRelationship: ViewerRelationship
  viewerHasBlocked: boolean
  viewerHasRestricted: boolean
}

export interface AuthTokens {
  accessToken: string
  tokenType: 'Bearer'
  expiresInSeconds: number
  user: UserSummary
}

export type PostType = 'PHOTO' | 'VIDEO' | 'REEL' | 'CAROUSEL'
export type MediaType = 'IMAGE' | 'VIDEO'
export type MediaStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'

export interface Media {
  id: number
  url: string
  mediaType: MediaType
  width: number | null
  height: number | null
  durationSec: number | null
  position: number
  thumbnailUrl: string | null
  status: MediaStatus
}

export interface Post {
  id: number
  author: UserSummary
  caption: string | null
  location: string | null
  type: PostType
  mediaCount: number
  likeCount: number
  commentCount: number
  likedByViewer: boolean
  savedByViewer: boolean
  createdAt: string
  media: Media[]
  hashtags: string[]
}

export interface PresignedUpload {
  uploadUrl: string
  objectKey: string
  publicUrl: string
}

export interface Comment {
  id: number
  author: UserSummary
  text: string
  parentCommentId: number | null
  likeCount: number
  createdAt: string
}

export type FollowStatus = 'PENDING' | 'ACCEPTED'

export interface FollowStatusResponse {
  status: FollowStatus
}

export interface LikeCountResponse {
  likeCount: number
  likedByViewer: boolean
}

export interface Story {
  id: number
  author: UserSummary
  mediaUrl: string
  expiresAt: string
  createdAt: string
  seenByViewer: boolean
}

export interface Insights {
  postCount: number
  followerCount: number
  followingCount: number
  totalLikes: number
  totalComments: number
}

export interface StoryHighlight {
  id: number
  title: string
  coverUrl: string | null
  createdAt: string
}

export interface StoryHighlightItem {
  id: number
  mediaUrl: string
  createdAt: string
}

export interface StoryHighlightDetail extends StoryHighlight {
  items: StoryHighlightItem[]
}

export type NotificationType = 'LIKE' | 'COMMENT' | 'FOLLOW' | 'FOLLOW_REQUEST' | 'FOLLOW_REQUEST_ACCEPTED'

export interface Notification {
  id: number
  actor: UserSummary
  type: NotificationType
  targetType: string
  targetId: number
  read: boolean
  createdAt: string
}

export interface Conversation {
  id: number
  group: boolean
  participants: UserSummary[]
  createdAt: string
}

export interface Message {
  id: number
  conversationId: number
  sender: UserSummary
  content: string | null
  mediaUrl: string | null
  createdAt: string
}

export interface UserSearchResult {
  id: number
  username: string
  fullName: string | null
  profilePictureUrl: string | null
  isVerified: boolean
}

export interface PostSearchResult {
  id: number
  caption: string
  authorId: number
  authorUsername: string
  hashtags: string[]
  createdAt: string
}
