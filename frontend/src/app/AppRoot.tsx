import type { Location } from 'react-router-dom'
import { useLocation, useRoutes } from 'react-router-dom'
import { modalRoutes, routes } from './routes'

interface LocationState {
  backgroundLocation?: Location
}

/**
 * Implements react-router's "background location" recipe: the main route tree is matched against
 * the background location (if navigation state carries one) so the page behind a modal stays
 * mounted, while the modal route tree is matched against the *real* location and rendered as an
 * overlay on top. A direct load of e.g. /p/:postId carries no backgroundLocation, so it falls
 * through to the plain full-page route instead.
 */
export function AppRoot() {
  const location = useLocation()
  const backgroundLocation = (location.state as LocationState | null)?.backgroundLocation

  const mainElement = useRoutes(routes, backgroundLocation ?? location)
  const modalElement = useRoutes(modalRoutes, location)

  return (
    <>
      {mainElement}
      {backgroundLocation ? modalElement : null}
    </>
  )
}
