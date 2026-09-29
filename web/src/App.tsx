import { useEffect } from 'react'
import { RouterProvider } from 'react-router-dom'
import { router } from './router'
import { useTheme } from './lib/useTheme'
import { useLockStore } from './state/lockStore'

export default function App(): React.ReactElement {
  useTheme()

  const touchActivity = useLockStore((s) => s.touchActivity)
  const checkAutoRelock = useLockStore((s) => s.checkAutoRelock)

  useEffect(() => {
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

  return <RouterProvider router={router} />
}
