import { Outlet, type RouteObject } from 'react-router-dom'
import { ForgotPasswordPage } from '@/features/auth/ForgotPasswordPage'
import { LoginPage } from '@/features/auth/LoginPage'
import { RegisterPage } from '@/features/auth/RegisterPage'
import { ResetPasswordPage } from '@/features/auth/ResetPasswordPage'
import { CreatePostModal } from '@/features/create-post/CreatePostModal'
import { ExplorePage } from '@/features/explore/ExplorePage'
import { HashtagPage } from '@/features/hashtag/HashtagPage'
import { HomeFeedPage } from '@/features/feed/HomeFeedPage'
import { ConversationThread } from '@/features/messaging/ConversationThread'
import { DirectInboxPage } from '@/features/messaging/DirectInboxPage'
import { EditProfilePage } from '@/features/profile/EditProfilePage'
import { BlockedMutedPage } from '@/features/profile/BlockedMutedPage'
import { FollowRequestsPage } from '@/features/profile/FollowRequestsPage'
import { InsightsPage } from '@/features/insights/InsightsPage'
import { NotificationsPage } from '@/features/notifications/NotificationsPage'
import { PostDetailModal } from '@/features/post-detail/PostDetailModal'
import { PostPage } from '@/features/post-detail/PostPage'
import { ProfilePage } from '@/features/profile/ProfilePage'
import { ReelsPage } from '@/features/reels/ReelsPage'
import { UserListModal } from '@/features/profile/UserListModal'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { AppShell } from './layout/AppShell'
import { RequireAuth } from './RequireAuth'

/**
 * The full route tree, matched against either the real location or a "background" location saved
 * in navigation state — see AppRoot.tsx for how the background-location modal pattern uses this.
 */
export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/reset-password', element: <ResetPasswordPage /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppShell />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <HomeFeedPage /> },
      { path: 'explore', element: <ExplorePage /> },
      { path: 'explore/tags/:tag', element: <HashtagPage /> },
      { path: 'reels', element: <ReelsPage /> },
      { path: 'create', element: <CreatePostModal /> },
      { path: 'notifications', element: <NotificationsPage /> },
      {
        path: 'direct/inbox',
        element: <DirectInboxPage />,
        children: [{ path: ':conversationId', element: <ConversationThread /> }],
      },
      {
        path: 'direct/requests',
        element: <DirectInboxPage />,
        children: [{ path: ':conversationId', element: <ConversationThread /> }],
      },
      { path: 'p/:postId', element: <PostPage /> },
      { path: 'accounts/edit', element: <EditProfilePage /> },
      { path: 'accounts/insights', element: <InsightsPage /> },
      { path: 'accounts/follow-requests', element: <FollowRequestsPage /> },
      { path: 'accounts/blocked-muted', element: <BlockedMutedPage /> },
      { path: ':username', element: <ProfilePage /> },
      { path: ':username/followers', element: <UserListModal mode="followers" /> },
      { path: ':username/following', element: <UserListModal mode="following" /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]

/**
 * Routes that render as an overlay on top of a background location instead of a full page —
 * matched against the *real* current location, only rendered when navigation state carries a
 * backgroundLocation (see PostCard's detailLinkState). Each one mirrors a path in `routes` above
 * so a direct load / hard refresh at the same URL renders the equivalent full-page version instead.
 */
export const modalRoutes: RouteObject[] = [
  {
    path: '/',
    element: (
      <RequireAuth>
        <Outlet />
      </RequireAuth>
    ),
    children: [
      { path: 'p/:postId', element: <PostDetailModal /> },
      { path: 'create', element: <CreatePostModal /> },
      { path: ':username/followers', element: <UserListModal mode="followers" /> },
      { path: ':username/following', element: <UserListModal mode="following" /> },
      // AppRoot's useRoutes(modalRoutes, location) call is unconditional (rules of hooks), so on
      // every page that isn't one of the modal paths above this would otherwise log a spurious
      // "No routes matched" warning — a catch-all silences it without changing behavior, since its
      // result is only ever rendered when `backgroundLocation` is set (see AppRoot.tsx).
      { path: '*', element: null },
    ],
  },
]
