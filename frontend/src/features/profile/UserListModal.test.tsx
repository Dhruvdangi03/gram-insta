import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '@/contexts/authContextDefinition'
import type { UserSummary } from '@/lib/api/types'
import { UserListModal } from './UserListModal'

const FOLLOWERS: UserSummary[] = [
  { id: 1, username: 'jake', fullName: 'Jake Wanders', profilePictureUrl: null, isVerified: false },
  { id: 2, username: 'kim', fullName: 'Kim Park', profilePictureUrl: null, isVerified: false },
]

/** Serves the followers list for GET and `removeStatus` for the DELETE a removal sends. */
function stubApi(removeStatus: number) {
  const fetchMock = vi.fn((_url: string, init?: RequestInit) => {
    if (init?.method === 'DELETE') {
      return Promise.resolve({
        ok: removeStatus < 400,
        status: removeStatus,
        json: () => Promise.resolve({ type: 'about:blank', title: 'Error', status: removeStatus, detail: 'nope' }),
      })
    }
    return Promise.resolve({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ items: FOLLOWERS, nextCursor: null, hasMore: false }),
    })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function renderFollowersOf(profileUsername: string, viewerUsername: string) {
  const auth: AuthContextValue = {
    status: 'authenticated',
    user: { id: 99, username: viewerUsername, fullName: null, profilePictureUrl: null, isVerified: false },
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    updateUser: vi.fn(),
  }
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <AuthContext.Provider value={auth}>
        <MemoryRouter initialEntries={[`/${profileUsername}/followers`]}>
          <Routes>
            <Route path=":username/followers" element={<UserListModal mode="followers" />} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
}

async function removeJake(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: 'Remove jake' }))
  await user.click(await screen.findByRole('button', { name: 'Remove @jake' }))
}

describe('UserListModal — removing a follower', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it("gives each Remove button the row's username as its accessible name", async () => {
    stubApi(204)
    renderFollowersOf('luna', 'luna')

    expect(await screen.findByRole('button', { name: 'Remove jake' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Remove kim' })).toBeInTheDocument()
  })

  it('removes only that follower, after a confirmation', async () => {
    const fetchMock = stubApi(204)
    const user = userEvent.setup()
    renderFollowersOf('luna', 'luna')

    await user.click(await screen.findByRole('button', { name: 'Remove jake' }))
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false)

    await user.click(await screen.findByRole('button', { name: 'Remove @jake' }))

    await waitFor(() => expect(screen.queryByText('jake')).not.toBeInTheDocument())
    expect(screen.getByText('kim')).toBeInTheDocument()
    const deleteCall = fetchMock.mock.calls.find(([, init]) => init?.method === 'DELETE')
    expect(String(deleteCall?.[0])).toContain('/users/jake/follow/remove')
  })

  it('shows no Remove buttons on somebody else’s followers list', async () => {
    stubApi(204)
    renderFollowersOf('luna', 'someone_else')

    await screen.findByText('jake')
    expect(screen.queryByRole('button', { name: /^Remove/ })).not.toBeInTheDocument()
  })

  it('treats a 404 (already not following) as done — the row goes and no error is shown', async () => {
    stubApi(404)
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {})
    const user = userEvent.setup()
    renderFollowersOf('luna', 'luna')

    await removeJake(user)

    await waitFor(() => expect(screen.queryByText('jake')).not.toBeInTheDocument())
    expect(alertSpy).not.toHaveBeenCalled()
  })

  it('keeps the row and tells the user when the removal genuinely fails', async () => {
    stubApi(500)
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {})
    const user = userEvent.setup()
    renderFollowersOf('luna', 'luna')

    await removeJake(user)

    await waitFor(() => expect(alertSpy).toHaveBeenCalledTimes(1))
    expect(screen.getByText('jake')).toBeInTheDocument()
  })
})
