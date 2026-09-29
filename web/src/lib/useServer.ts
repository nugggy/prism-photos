import { useMemo } from 'react'
import { useSessionStore } from '../state/sessionStore'
import type { ServerRef } from '../plex/urls'

/** Returns the current ServerRef (base URL + server-scoped token), or null if not connected. */
export function useServer(): ServerRef | null {
  const activeConnection = useSessionStore((s) => s.activeConnection)
  const serverToken = useSessionStore((s) => s.serverToken)
  return useMemo(() => {
    if (!activeConnection || !serverToken) return null
    return { baseUrl: activeConnection.baseUrl, token: serverToken }
  }, [activeConnection, serverToken])
}
