import { Navigate, Outlet } from 'react-router-dom'
import { useSessionStore } from '../state/sessionStore'

export function RequireConnection(): React.ReactElement {
  const serverToken = useSessionStore((s) => s.serverToken)
  const activeConnection = useSessionStore((s) => s.activeConnection)
  if (!serverToken || !activeConnection) {
    return <Navigate to="/signin" replace />
  }
  return <Outlet />
}

export function RequireLibrary(): React.ReactElement {
  const sectionKey = useSessionStore((s) => s.selectedSectionKey)
  if (!sectionKey) {
    return <Navigate to="/servers" replace />
  }
  return <Outlet />
}
