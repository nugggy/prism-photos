import { useEffect } from 'react'
import { useSettingsStore } from '../state/settingsStore'

/** Applies the theme preference (system/light/dark) to the document root. */
export function useTheme(): void {
  const theme = useSettingsStore((s) => s.theme)
  useEffect(() => {
    const root = document.documentElement
    if (theme === 'system') {
      root.removeAttribute('data-theme')
    } else {
      root.setAttribute('data-theme', theme)
    }
  }, [theme])
}
