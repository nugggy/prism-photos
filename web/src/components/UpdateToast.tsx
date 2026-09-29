import { useRegisterSW } from 'virtual:pwa-register/react'

/** Shows a toast when a new service worker is waiting, so the app can self-update from GitHub Pages. */
export function UpdateToast(): React.ReactElement | null {
  const { needRefresh: [needRefresh, setNeedRefresh], updateServiceWorker } = useRegisterSW({
    onRegisterError: () => {
      /* PWA registration failing (e.g. dev mode) is not user-facing. */
    },
  })

  if (!needRefresh) return null

  return (
    <div className="toast" role="status">
      <span>Update available.</span>
      <button className="btn btn-primary" onClick={() => void updateServiceWorker(true)}>
        Reload
      </button>
      <button className="btn" onClick={() => setNeedRefresh(false)} aria-label="Dismiss">
        Later
      </button>
    </div>
  )
}
