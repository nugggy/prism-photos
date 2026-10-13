import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type AlbumSortOrder = 'newest' | 'oldest' | 'titleAsc' | 'titleDesc'

export interface AlbumPrefs {
  coverItemId?: string
  accentColor?: string
  sortOrder?: AlbumSortOrder
}

export interface AlbumPrefsState {
  prefs: Record<string, AlbumPrefs>
  setCover: (albumId: string, itemId: string) => void
  setAccentColor: (albumId: string, color: string) => void
  setSortOrder: (albumId: string, order: AlbumSortOrder) => void
  reset: () => void
}

const NO_PREFS: AlbumPrefs = {}

/** Selector for one album's prefs. Returns a stable empty object when none are set. */
export const selectAlbumPrefs = (albumId: string | undefined) => (s: AlbumPrefsState): AlbumPrefs => (albumId ? s.prefs[albumId] : undefined) ?? NO_PREFS

export const useAlbumPrefsStore = create<AlbumPrefsState>()(
  persist(
    (set) => ({
      prefs: {},
      setCover: (albumId, itemId) =>
        set((s) => ({ prefs: { ...s.prefs, [albumId]: { ...s.prefs[albumId], coverItemId: itemId } } })),
      setAccentColor: (albumId, color) =>
        set((s) => ({ prefs: { ...s.prefs, [albumId]: { ...s.prefs[albumId], accentColor: color } } })),
      setSortOrder: (albumId, order) =>
        set((s) => ({ prefs: { ...s.prefs, [albumId]: { ...s.prefs[albumId], sortOrder: order } } })),
      reset: () => set({ prefs: {} }),
    }),
    { name: 'prism.albumPrefs' },
  ),
)
