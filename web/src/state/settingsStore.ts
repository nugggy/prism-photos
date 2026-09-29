import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { ConnectionMode } from '../plex/connections'

export type ThemePreference = 'system' | 'light' | 'dark'
export type TranscodePreference = 'auto' | 'direct' | 'hls'

export interface SettingsState {
  theme: ThemePreference
  density: number // grid columns, 2-8
  connectionMode: ConnectionMode
  manualUrl: string
  transcodePreference: TranscodePreference
  slideshowIntervalSec: number

  setTheme: (theme: ThemePreference) => void
  setDensity: (density: number) => void
  setConnectionMode: (mode: ConnectionMode) => void
  setManualUrl: (url: string) => void
  setTranscodePreference: (pref: TranscodePreference) => void
  setSlideshowInterval: (seconds: number) => void
}

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set) => ({
      theme: 'system',
      density: 5,
      connectionMode: 'AUTO',
      manualUrl: '',
      transcodePreference: 'auto',
      slideshowIntervalSec: 5,

      setTheme: (theme) => set({ theme }),
      setDensity: (density) => set({ density: Math.min(8, Math.max(2, density)) }),
      setConnectionMode: (connectionMode) => set({ connectionMode }),
      setManualUrl: (manualUrl) => set({ manualUrl }),
      setTranscodePreference: (transcodePreference) => set({ transcodePreference }),
      setSlideshowInterval: (slideshowIntervalSec) => set({ slideshowIntervalSec }),
    }),
    { name: 'prism.settings' },
  ),
)
