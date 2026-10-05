import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { QueryClient } from '@tanstack/react-query'
import { ensureFreshSession } from '@/lib/api/client'
import { registerHandlers } from './handlers'

const WS_URL = import.meta.env.VITE_WS_URL as string

let client: Client | null = null

function createClient(queryClient: QueryClient): Client {
  const stomp = new Client({
    webSocketFactory: () => new SockJS(WS_URL) as unknown as WebSocket,
    reconnectDelay: 5000,
    // Called before every connection attempt, including stompjs's own automatic reconnects (e.g.
    // after the access token expires mid-connection and the server drops the socket per
    // StompAuthChannelInterceptor's per-frame expiry check) — using the same single-flight
    // ensureFreshSession() the REST 401-retry path uses means the two never race two independent
    // /auth/refresh calls against the backend's single-use rotating refresh token.
    //
    // Deliberately NOT also reconnecting in response to onTokenRotated: ensureFreshSession() here
    // itself rotates the token (that's what "fresh" means), which fires the rotated event — wiring
    // that back to "force a reconnect" turned every single connection attempt into an infinite
    // self-triggering reconnect loop (a real bug caught live: /ws/info was called 100+ times/sec).
    // beforeConnect already guarantees a fresh token on every attempt; a currently-open socket
    // doesn't need to be torn down just because some unrelated REST call rotated the token too.
    beforeConnect: async () => {
      const session = await ensureFreshSession()
      stomp.connectHeaders = { Authorization: `Bearer ${session.accessToken}` }
    },
    onConnect: () => registerHandlers(stomp, queryClient),
  })
  return stomp
}

/** Called once AuthContext reaches 'authenticated'. Idempotent — calling it again while already active is a no-op. */
export function connectStomp(queryClient: QueryClient) {
  if (client?.active) return
  client = createClient(queryClient)
  client.activate()
}

export function disconnectStomp() {
  void client?.deactivate()
  client = null
}
