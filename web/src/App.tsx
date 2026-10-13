import { useEffect } from 'react'
import { RouterProvider } from 'react-router-dom'
import { router } from './router'
import { useTheme } from './lib/useTheme'
import { useLockStore } from './state/lockStore'
import { useSessionStore } from './state/sessionStore'
import { showToast } from './state/toastStore'
import { setUnauthorisedHandler } from './plex/api'
import { Toaster } from './components/Toaster'

export default function App(): React.ReactElement {
  useTheme()

  const touchActivity = useLockStore((s) => s.touchActivity)
  const checkAutoRelock = useLockStore((s) => s.checkAutoRelock)

  useEffect(() => {
    // touchActivity is throttled in the store, so calling it on every key press is cheap.
    const onActivity = () => touchActivity()
    window.addEventListener('pointerdown', onActivity)
    window.addEventListener('keydown', onActivity)
    const interval = setInterval(checkAutoRelock, 15_000)
    return () => {
      window.removeEventListener('pointerdown', onActivity)
      window.removeEventListener('keydown', onActivity)
      clearInterval(interval)
    }
  }, [touchActivity, checkAutoRelock])

  // A 401 from the connected server means the token is no longer valid: clear the session and
  // send the user to sign in. Requests made with some other token (for example while trying a
  // server in the picker) are left to the caller.
  useEffect(() => {
    setUnauthorisedHandler((server) => {
      const session = useSessionStore.getState()
      if (!session.serverToken || session.serverToken !== server.token) return
      session.signOut()
      showToast('Your Plex session has expired. Please sign in again.')
      void router.navigate('/signin', { replace: true })
    })
    return () => setUnauthorisedHandler(null)
  }, [])

  return (
    <>
      <RouterProvider router={router} />
      <Toaster />
    </>
  )
}
